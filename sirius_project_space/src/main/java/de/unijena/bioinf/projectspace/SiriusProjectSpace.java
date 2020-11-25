/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.projectspace;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.chem.RetentionTime;
import de.unijena.bioinf.ChemistryBase.ms.DetectedAdducts;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.utils.FileUtils;
import de.unijena.bioinf.jjobs.TinyBackgroundJJob;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResult;
import de.unijena.bioinf.projectspace.sirius.FormulaResultRankingScore;
import de.unijena.bioinf.projectspace.sirius.SiriusLocations;
import de.unijena.bioinf.sirius.FTreeMetricsHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SiriusProjectSpace implements Iterable<CompoundContainerId>, AutoCloseable {

    private Path root;

    protected final ConcurrentHashMap<String, CompoundContainerId> ids;
    protected final ProjectSpaceConfiguration configuration;
    protected final AtomicInteger compoundCounter;
    private final ConcurrentHashMap<Class<? extends ProjectSpaceProperty>, ProjectSpaceProperty> projectSpaceProperties;

    protected ConcurrentLinkedQueue<ProjectSpaceListener> projectSpaceListeners;
    protected ConcurrentLinkedQueue<ContainerListener> compoundListeners, formulaResultListener;

    protected SiriusProjectSpace(ProjectSpaceConfiguration configuration, Path root) {
        this.configuration = configuration;
        this.ids = new ConcurrentHashMap<>();
        this.root = root;
        this.compoundCounter = new AtomicInteger(-1);
        this.projectSpaceListeners = new ConcurrentLinkedQueue<>();
        this.projectSpaceProperties = new ConcurrentHashMap<>();
        this.compoundListeners = new ConcurrentLinkedQueue<>();
        this.formulaResultListener = new ConcurrentLinkedQueue<>();
    }

    public synchronized Path getRootPath() {
        return root;
    }

    public Path getLocation() {
        if (root.getFileSystem().equals(FileSystems.getDefault()))
            return root;
        else
            return Path.of(root.getFileSystem().toString());
    }

    public void addProjectSpaceListener(ProjectSpaceListener listener) {
        projectSpaceListeners.add(listener);
    }

    public ContainerListener.PartiallyListeningFluentBuilder<CompoundContainerId, CompoundContainer> defineCompoundListener() {
        return new ContainerListener.PartiallyListeningFluentBuilder<>(compoundListeners);
    }

    public ContainerListener.PartiallyListeningFluentBuilder<FormulaResultId, FormulaResult> defineFormulaResultListener() {
        return new ContainerListener.PartiallyListeningFluentBuilder<>(formulaResultListener);
    }

    protected void fireProjectSpaceChange(ProjectSpaceEvent event) {
        for (ProjectSpaceListener listener : this.projectSpaceListeners)
            listener.projectSpaceChanged(event);
    }

    protected synchronized void open() throws IOException {
        ids.clear();
        int maxIndex = -1;

        for (Path dir : FileUtils.listAndClose(root, l -> l.filter(Files::isDirectory).collect(Collectors.toList()))) {
            final Path expInfo = dir.resolve(SiriusLocations.COMPOUND_INFO);
            if (Files.exists(expInfo)) {
                final Map<String, String> keyValues = FileUtils.readKeyValues(expInfo);
                final int index = Integer.parseInt(keyValues.getOrDefault("index", "-1"));
                final String name = keyValues.getOrDefault("name", "");
                final String dirName = dir.getFileName().toString();
                final Double ionMass = Optional.ofNullable(keyValues.get("ionMass")).map(Double::parseDouble).orElse(null);
                final RetentionTime rt = Optional.ofNullable(keyValues.get("rt")).map(RetentionTime::fromStringValue).orElse(null);

                final PrecursorIonType ionType = Optional.ofNullable(keyValues.get("ionType"))
                        .flatMap(PrecursorIonType::parsePrecursorIonType).orElse(null);

                final CompoundContainerId cid = new CompoundContainerId(dirName, name, index, ionMass, ionType, rt);

                cid.setDetectedAdducts(
                        Optional.ofNullable(keyValues.get("detectedAdducts")).map(DetectedAdducts::fromString).orElse(null));

                cid.setRankingScoreTypes(
                        Optional.ofNullable(keyValues.get(CompoundContainerId.RANKING_KEY))
                                .flatMap(FormulaResultRankingScore::parseFromString).map(FormulaResultRankingScore::value)
                                .orElse(Collections.emptyList()));

                ids.put(dirName, cid);
                maxIndex = Math.max(index, maxIndex);
            }
        }

        this.compoundCounter.set(maxIndex + 1);
        fireProjectSpaceChange(ProjectSpaceEvent.OPENED);
    }

    public synchronized void close() throws IOException {
        try {
            this.ids.clear();
            FileUtils.closeIfNotDefaultFS(root);
        } finally {
            fireProjectSpaceChange(ProjectSpaceEvent.CLOSED);
        }
    }


    public Optional<CompoundContainerId> findCompound(String dirName) {
        return Optional.ofNullable(ids.get(dirName));
    }


    public Optional<CompoundContainer> newCompoundWithUniqueId(@NotNull String compoundName, @NotNull IntFunction<String> index2dirName) {
        return newCompoundWithUniqueId(compoundName, index2dirName, null);
    }

    public Optional<CompoundContainer> newCompoundWithUniqueId(@NotNull String compoundName, @NotNull IntFunction<String> index2dirName, @Nullable Ms2Experiment exp) {
        double ionMass = exp != null ? exp.getIonMass() : Double.NaN;
        RetentionTime rt = exp != null ? exp.getAnnotation(RetentionTime.class).orElse(null) : null;
        PrecursorIonType iontype = exp != null ? exp.getPrecursorIonType() : null;

        return newUniqueCompoundId(compoundName, index2dirName, ionMass, iontype, rt)
                .map(idd -> {
                    try {
                        idd.containerLock.lock();
                        final CompoundContainer comp = getContainer(CompoundContainer.class, idd);
                        if (exp != null) {
                            comp.setAnnotation(Ms2Experiment.class, exp);
                            updateContainer(CompoundContainer.class, comp, Ms2Experiment.class);
                        }
                        fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.CREATED, comp.getId(), comp, Set.of(Ms2Experiment.class)));
//                        fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.UPDATED, comp.getId(), comp, Set.of(Ms2Experiment.class)));
                        return comp;
                    } catch (IOException e) {
                        return null;
                    } finally {
                        idd.containerLock.unlock();
                    }
                });
    }


    public Optional<CompoundContainerId> newUniqueCompoundId(String compoundName, IntFunction<String> index2dirName) {
        return newUniqueCompoundId(compoundName, index2dirName, Double.NaN, null, null);
    }


    public Optional<CompoundContainerId> newUniqueCompoundId(String compoundName, IntFunction<String> index2dirName, double ioMass, PrecursorIonType ionType, RetentionTime rt) {
        int index = compoundCounter.getAndIncrement();
        String dirName = index2dirName.apply(index);

        Optional<CompoundContainerId> cidOpt = tryCreateCompoundContainer(dirName, compoundName, index, ioMass, ionType, rt);
        cidOpt.ifPresent(cid ->
                fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.ID_CREATED, cid, null, Collections.emptySet())));
        return cidOpt;
    }

    public Optional<FormulaResultId> newUniqueFormulaResultId(@NotNull CompoundContainerId id, @NotNull FTree tree) throws IOException {
        return newFormulaResultWithUniqueId(getCompound(id), tree).map(FormulaResult::getId);
    }

    public Optional<FormulaResult> newFormulaResultWithUniqueId(@NotNull final CompoundContainer container, @NotNull final FTree tree) {
        if (!containsCompound(container.getId()))
            throw new IllegalArgumentException("Compound is not part of the project Space! ID: " + container.getId());
        final PrecursorIonType ionType = tree.getAnnotationOrThrow(PrecursorIonType.class);
        final MolecularFormula f = tree.getRoot().getFormula().add(ionType.getAdduct()).subtract(ionType.getInSourceFragmentation()); //get precursor formula
        final FormulaResultId fid = new FormulaResultId(container.getId(), f, ionType);

        if (container.contains(fid))
            throw new IllegalArgumentException("FormulaResult '" + fid + "' does already exist for compound '" + container.getId() + "' " + container.getId());

        final FormulaResult r = new FormulaResult(fid);
        r.setAnnotation(FTree.class, tree);
        r.setAnnotation(FormulaScoring.class, new FormulaScoring(FTreeMetricsHelper.getScoresFromTree(tree)));

        try {
            container.getId().containerLock.lock();
            updateContainer(FormulaResult.class, r, FTree.class, FormulaScoring.class);
            //modify input container
            container.getResults().put(r.getId().fileName(), r.getId());

            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.CREATED, r.getId(), r, Collections.emptySet()));

        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Could not create FormulaResult from FTree!", e);
            return Optional.empty();
        } finally {
            container.getId().containerLock.unlock();
        }
        return Optional.of(r);
    }

    private void fireContainerListeners(ConcurrentLinkedQueue<ContainerListener> formulaResultListener, ContainerEvent<CompoundContainerId, CompoundContainer> event) {
        formulaResultListener.forEach(x -> x.containerChanged(event));
    }

    //this is used for quick filesystem base copying
    protected void fireCompoundCreated(CompoundContainerId id) throws IOException {
        try {
            id.containerLock.lock();
            CompoundContainer comp = getContainer(CompoundContainer.class, id);
            fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.CREATED, id, comp, Collections.emptySet()));
        } finally {
            id.containerLock.unlock();
        }
    }

    protected Optional<CompoundContainerId> tryCreateCompoundContainer(String directoryName, String compoundName, int compoundIndex, double ionMass, PrecursorIonType ionType, RetentionTime rt) {
        if (containsCompound(directoryName)) return Optional.empty();
        synchronized (ids) {
            if (Files.exists(root.resolve(directoryName)))
                return Optional.empty();
            CompoundContainerId id = new CompoundContainerId(directoryName, compoundName, compoundIndex, ionMass, ionType, rt);
            if (ids.put(directoryName, id) != null)
                return Optional.empty();
            try {
                Files.createDirectory(root.resolve(directoryName));
                writeCompoundContainerID(id);
                return Optional.of(id);
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).error("cannot create directory " + directoryName, e);
                ids.remove(id.getDirectoryName());
                return Optional.empty();
            }
        }
    }

    public void updateCompoundContainerID(CompoundContainerId cid) throws IOException {
        if (cid == null || ids.get(cid.getDirectoryName()) != cid)
            return;

        cid.containerLock.lock();
        try {
            writeCompoundContainerID(cid);
        } finally {
            cid.containerLock.unlock();
        }
    }

    private void writeCompoundContainerID(CompoundContainerId cid) throws IOException {
        final Path f = root.resolve(cid.getDirectoryName()).resolve(SiriusLocations.COMPOUND_INFO);
        Files.deleteIfExists(f);
        try (final BufferedWriter bw = Files.newBufferedWriter(f)) {
            for (Map.Entry<String, String> kv : cid.asKeyValuePairs().entrySet()) {
                bw.write(kv.getKey() + "\t" + kv.getValue());
                bw.newLine();
            }
        }
        fireProjectSpaceChange(ProjectSpaceEvent.INDEX_UPDATED);
    }

    // shorthand methods
    @SafeVarargs
    public final List<SScored<FormulaResult, ? extends FormulaScore>> getFormulaResultsOrderedBy(CompoundContainerId cid, List<Class<? extends FormulaScore>> scores, Class<? extends DataAnnotation>... components) throws IOException {
        return getFormulaResultsOrderedBy(getCompound(cid).getResults().values(), scores, components);
    }

    @SafeVarargs
    public final List<SScored<FormulaResult, ? extends FormulaScore>> getFormulaResultsOrderedBy(Collection<FormulaResultId> results, List<Class<? extends FormulaScore>> scores, Class<? extends DataAnnotation>... components) throws IOException {
        ArrayList<Class<? extends DataAnnotation>> comps = new ArrayList<>(components.length + 1);
        comps.addAll(Arrays.asList(components));
        if (!comps.contains(FormulaScoring.class))
            comps.add(FormulaScoring.class);

        //not stream because IOExceptions
        List<FormulaResult> res = new ArrayList<>(results.size());
        for (FormulaResultId fid : results)
            res.add(getFormulaResult(fid, comps.toArray(Class[]::new)));

        return FormulaScoring.rankBy(res,scores,true);

//                res.stream().map(fr -> {
//            T fs = fr.getAnnotation(FormulaScoring.class).map(sc -> sc.getAnnotationOr(score, FormulaScore::NA)).orElse(FormulaScore.NA(score));
//                return new SScored<>(fr, fs);
//        }).sorted(Collections.reverseOrder()).collect(Collectors.toList());
    }

    @SafeVarargs
    public final FormulaResult getFormulaResult(FormulaResultId id, Class<? extends DataAnnotation>... components) throws IOException {
        CompoundContainerId parentId = id.getParentId();
        parentId.containerLock.lock();
        try {
            return getContainer(FormulaResult.class, id, components);
        } finally {
            parentId.containerLock.unlock();
        }
    }

    @SafeVarargs
    public final void updateFormulaResult(FormulaResult result, Class<? extends DataAnnotation>... components) throws IOException {
        CompoundContainerId parentId = result.getId().getParentId();
        parentId.containerLock.lock();
        try {
            updateContainer(FormulaResult.class, result, components);
            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.UPDATED, result.getId(), result, new HashSet<>(Arrays.asList(components))));
        } finally {
            parentId.containerLock.unlock();
        }
    }

    /**
     *  Deletes annotations of FormulaResults.
     *  Be careful results are defined by their
     * @param resultId
     * @param components
     * @throws IOException if io error occurs
     */
    @SafeVarargs
    public final void deleteFromFormulaResult(FormulaResultId resultId, Class<? extends DataAnnotation>... components) throws IOException {
        final CompoundContainerId parentId = resultId.getParentId();
        parentId.containerLock.lock();
        try {
            deleteFromContainer(FormulaResult.class, resultId, List.of(components));
            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.UPDATED, resultId, null, Set.of(components)));
        } finally {
            parentId.containerLock.unlock();
        }
    }

    public final void deleteFormulaResult(FormulaResultId resultId) throws IOException {
        CompoundContainerId parentId = resultId.getParentId();
        parentId.containerLock.lock();
        try {
            deleteContainer(FormulaResult.class, resultId);
            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.DELETED, resultId, null, Collections.emptySet()));
        } finally {
            parentId.containerLock.unlock();
        }
    }


    @SafeVarargs
    public final CompoundContainer getCompound(CompoundContainerId id, Class<? extends DataAnnotation>... components) throws IOException {
        id.containerLock.lock();
        try {
            return getContainer(CompoundContainer.class, id, components);
        } finally {
            id.containerLock.unlock();
        }
    }

    @SafeVarargs
    public final void updateCompound(CompoundContainer compound, Class<? extends DataAnnotation>... components) throws IOException {
        final CompoundContainerId id = compound.getId();
        id.containerLock.lock();
        try {
            updateContainer(CompoundContainer.class, compound, components);
            fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.UPDATED, compound.getId(), compound, new HashSet<>(Arrays.asList(components))));
        } finally {
            id.containerLock.unlock();
        }
    }

    public void deleteCompound(CompoundContainerId cid) throws IOException {
        cid.containerLock.lock();
        try {
            if (ids.remove(cid.getDirectoryName()) != null) {
                deleteContainer(CompoundContainer.class, cid);
                fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.DELETED, cid, null, Collections.emptySet()));
                fireProjectSpaceChange(ProjectSpaceEvent.INDEX_UPDATED);
            }
        } finally {
            cid.containerLock.unlock();
        }
    }

    public boolean renameCompound(CompoundContainerId oldId, String name, IntFunction<String> index2dirName) {
        oldId.containerLock.lock();
        try {
            final String newDirName = index2dirName.apply(oldId.getCompoundIndex());
            synchronized (ids) {
                if (newDirName.equals(oldId.getDirectoryName())) {
                    try {
                        if (name.equals(oldId.getCompoundName()))
                            return true; //nothing to do
                        oldId.rename(name, newDirName);
                        writeCompoundContainerID(oldId);
                        return true; //renamed but no move needed
                    } catch (IOException e) {
                        LoggerFactory.getLogger(SiriusProjectSpace.class).error("cannot write changed ID. Renaming may not be persistent", e);
                        return true; //rename failed due ioError
                    }
                }

                if (ids.containsKey(newDirName))
                    return false; // rename not possible because key already exists

                final Path file = root.resolve(newDirName);
                if (Files.exists(file)) {
                    return false; // rename not target directory already exists
                }

                try {
                    Files.move(root.resolve(oldId.getDirectoryName()), file);
                    //change id only if move was successful
                    ids.remove(oldId.getDirectoryName());
                    oldId.rename(name, newDirName);
                    ids.put(oldId.getDirectoryName(), oldId);
                    writeCompoundContainerID(oldId);
                    return true;
                } catch (IOException e) {
                    LoggerFactory.getLogger(SiriusProjectSpace.class).error("cannot move directory", e);
                    return false; // move failed due to an error
                }

            }
        } finally {
            oldId.containerLock.unlock();
        }
    }


    // generic methods

    @SafeVarargs
    final <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    Container getContainer(Class<Container> klass, Id id, Class<? extends DataAnnotation>... components) throws IOException {
        // read container
        final Container container = configuration.getContainerSerializer(klass).readFromProjectSpace(new FileBasedProjectSpaceReader(root, this::getProjectSpaceProperty), (r, c, f) -> {
            // read components
            for (Class k : components) {
                f.apply((Class<DataAnnotation>) k, (DataAnnotation) configuration.getComponentSerializer(klass, k).read(r, id, c));
            }
        }, id);
        return container;
    }

    @SafeVarargs
    final <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    void updateContainer(Class<Container> klass, Container container, Class<? extends DataAnnotation>... components) throws IOException {
        // write container
        configuration.getContainerSerializer(klass).writeToProjectSpace(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), (r, c, f) -> {
            // write components
            for (Class k : components) {
                configuration.getComponentSerializer(klass, k)
                        .write(r, container.getId(), container, f.apply(k));
            }
        }, container.getId(), container);
    }

    final <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    void deleteContainer(Class<Container> klass, Id containerId) throws IOException {
        deleteFromContainer(klass, containerId, configuration.getAllComponentsForContainer(klass));
    }

    final <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    void deleteFromContainer(Class<Container> klass, Id containerId, List<Class> components) throws IOException {
        //delete container components
        configuration.getContainerSerializer(klass).deleteFromProjectSpace(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), (w, id) -> {
            // delete components
            for (Class k : components)
                configuration.getComponentSerializer(klass, k).delete(w, id);
        }, containerId);
    }

    @NotNull
    public Collection<CompoundContainerId> getIds() {
        return Collections.unmodifiableCollection(ids.values());
    }

    @NotNull
    @Override
    public Iterator<CompoundContainerId> iterator() {
        return ids.values().iterator();
    }

    public Iterator<CompoundContainerId> filteredIterator(Predicate<CompoundContainerId> predicate) {
        return ids.values().stream().filter(predicate).iterator();
    }

    @SafeVarargs
    public final Iterator<CompoundContainer> compoundIterator(Class<? extends DataAnnotation>... components) {
        return new CompoundContainerIterator(this, components);
    }

    @SafeVarargs
    public final Iterator<CompoundContainer> filteredCompoundIterator(@Nullable Predicate<CompoundContainerId> prefilter, @Nullable Predicate<CompoundContainer> filter, @NotNull Class<? extends DataAnnotation>... components) {
        return new CompoundContainerIterator(this, prefilter, filter, components);
    }

    public final Iterator<CompoundContainer> filteredCompoundIterator(@Nullable Predicate<CompoundContainerId> prefilter, @Nullable Predicate<Ms2Experiment> filter) {
        return new CompoundContainerIterator(this, prefilter, filter != null ? (c) -> filter.test(c.getAnnotationOrThrow(Ms2Experiment.class)) : null, Ms2Experiment.class);
    }

    public int size() {
        return compoundCounter.get();
    }

    public final <T extends ProjectSpaceProperty> Optional<T> getProjectSpaceProperty(Class<T> key) {
        T property = (T) projectSpaceProperties.get(key);
        if (property == null) {
            synchronized (this) {
                synchronized (projectSpaceProperties) {
                    property = (T) projectSpaceProperties.get(key);
                    if (property != null) return Optional.of(property);
                    try {
                        T read = configuration.getProjectSpacePropertySerializer(key).read(new FileBasedProjectSpaceReader(root, this::getProjectSpaceProperty), null, null);
                        if (read == null)
                            return Optional.empty();

                        projectSpaceProperties.put(key, read);
                        return Optional.of(read);
                    } catch (IOException e) {
                        LoggerFactory.getLogger(SiriusProjectSpace.class).error(e.getMessage(), e);
                        return Optional.empty();
                    }

                }
            }
        } else return Optional.of(property);
    }

    public final  synchronized <T extends ProjectSpaceProperty> T setProjectSpaceProperty(Class<T> key, T value) {
        synchronized (projectSpaceProperties) {
            if (value == null)
                return deleteProjectSpaceProperty(key);

            try {
                configuration.getProjectSpacePropertySerializer(key).write(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), null, null, value != null ? Optional.of(value) : Optional.empty());
            } catch (IOException e) {
                LoggerFactory.getLogger(SiriusProjectSpace.class).error(e.getMessage(), e);
            }
            return (T) projectSpaceProperties.put(key, value);
        }
    }

    public final  synchronized <T extends ProjectSpaceProperty> T deleteProjectSpaceProperty(Class<T> key) {
        synchronized (projectSpaceProperties) {
            try {
                configuration.getProjectSpacePropertySerializer(key).delete(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), null);
            } catch (IOException e) {
                LoggerFactory.getLogger(SiriusProjectSpace.class).error(e.getMessage(), e);
            }
            return (T) projectSpaceProperties.remove(key);
        }
    }

    public boolean containsCompound(String dirName) {
        return findCompound(dirName).isPresent();
    }

    public boolean containsCompound(CompoundContainerId id) {
        return containsCompound(id.getDirectoryName());
    }

    protected synchronized boolean withAllLockedDo(IOCallable<Boolean> code) throws IOException {
        try {
            return withAllLockedDoRaw(code);
        } catch (InterruptedException e) {
            throw new IOException(e); //ugly but will not happen anyways
        }
    }

    protected synchronized boolean withAllLockedDoRaw(IOCallable<Boolean> code) throws IOException, InterruptedException {
        //todo do we need more locks to move the space?
        try {
            ids.values().forEach(cid -> cid.containerLock.lock());
            return code.call();
        } finally {
            ids.values().forEach(cid -> cid.containerLock.unlock());
        }
    }

    protected boolean changeLocation(Path nuLocation) throws IOException {
        return withAllLockedDo(() -> {
            final FileSystem fs = root.getFileSystem();
            if (!fs.equals(FileSystems.getDefault()) && fs.isOpen())
                fs.close();

            root = nuLocation;
            fireProjectSpaceChange(ProjectSpaceEvent.LOCATION_CHANGED);
            return true;
        });
    }

    @FunctionalInterface
    protected interface IOCallable<V> extends Callable<V> {
        @Override
        V call() throws IOException, InterruptedException;
    }

    public Class[] getRegisteredFormulaResultComponents() {
        return configuration.getAllComponentsForContainer(FormulaResult.class).toArray(Class[]::new);
    }

    public Class[] getRegisteredCompoundComponents() {
        return configuration.getAllComponentsForContainer(CompoundContainer.class).toArray(Class[]::new);
    }

    public void updateSummaries(Summarizer... summarizers) throws IOException {
        try {
            makeSummarizerJob(summarizers).compute();
        } catch (InterruptedException e) {
            throw new IOException(e); //bit ugly but will not happen anyways
        }
    }

    public SummarizerJob makeSummarizerJob(Summarizer... summarizers) {
        return new SummarizerJob(summarizers);
    }


    public class SummarizerJob extends TinyBackgroundJJob<Boolean> {

        private final Summarizer[] summarizers;

        protected SummarizerJob(Summarizer... summarizers) {
            this.summarizers = summarizers;
        }

        @Override
        protected Boolean compute() throws IOException, InterruptedException {
            int max = ids.size() + summarizers.length + 1;
            AtomicInteger p = new AtomicInteger(0);
            updateProgress(0, max, p.get(), "Collection Summary data...");
            checkForInterruption();
            return withAllLockedDoRaw(() -> {
                Class[] annotations = Arrays.stream(summarizers).flatMap(s -> s.requiredFormulaResultAnnotations().stream()).distinct().collect(Collectors.toList()).toArray(Class[]::new);
                for (CompoundContainerId cid : ids.values()) {
                    updateProgress(0, max, p.incrementAndGet(), "Collection '" + cid.getCompoundName() + "'...");
                    checkForInterruption();
                    final CompoundContainer c = getCompound(cid, Ms2Experiment.class);
                    final List<SScored<FormulaResult, ? extends FormulaScore>> results = getFormulaResultsOrderedBy(cid, cid.getRankingScoreTypes(), annotations);
                    for (Summarizer sim : summarizers)
                        sim.addWriteCompoundSummary(new FileBasedProjectSpaceWriter(root, SiriusProjectSpace.this::getProjectSpaceProperty), c, results);
                    checkForInterruption();
                }
                checkForInterruption();
                //write summaries to project space
                for (Summarizer summarizer : summarizers) {
                    checkForInterruption();
                    updateProgress(0, max, p.incrementAndGet(), "Writing Summary '" + summarizer.getClass().getSimpleName() + "'...");
                    summarizer.writeProjectSpaceSummary(new FileBasedProjectSpaceWriter(root, SiriusProjectSpace.this::getProjectSpaceProperty));
                }
                updateProgress(0, max, max, "DONE!");
                return true;
            });
        }

    }
}
