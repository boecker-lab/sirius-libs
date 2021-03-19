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

package de.unijena.bioinf.lcms;

import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.exceptions.InvalidInputData;
import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.ChemistryBase.math.ExponentialDistribution;
import de.unijena.bioinf.ChemistryBase.math.Statistics;
import de.unijena.bioinf.ChemistryBase.ms.CollisionEnergy;
import de.unijena.bioinf.ChemistryBase.ms.Deviation;
import de.unijena.bioinf.ChemistryBase.ms.lcms.CoelutingTraceSet;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleMutableSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.jjobs.BasicJJob;
import de.unijena.bioinf.jjobs.JobManager;
import de.unijena.bioinf.jjobs.ProgressJJob;
import de.unijena.bioinf.lcms.align.*;
import de.unijena.bioinf.lcms.ionidentity.IonNetwork;
import de.unijena.bioinf.lcms.noise.Ms2NoiseStatistics;
import de.unijena.bioinf.lcms.noise.NoiseStatistics;
import de.unijena.bioinf.lcms.peakshape.CustomPeakShape;
import de.unijena.bioinf.lcms.peakshape.CustomPeakShapeFitting;
import de.unijena.bioinf.lcms.peakshape.PeakShape;
import de.unijena.bioinf.lcms.quality.Quality;
import de.unijena.bioinf.model.lcms.*;
import de.unijena.bionf.spectral_alignment.CosineQueryUtils;
import de.unijena.bionf.spectral_alignment.IntensityWeightedSpectralAlignment;
import gnu.trove.list.array.TDoubleArrayList;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LCMSProccessingInstance {
    public static final String POSSIBLE_ADDUCTS_KEY = "lcms-align";//LCMSProccessingInstance.class.getSimpleName();

    protected HashMap<ProcessedSample, SpectrumStorage> storages;
    protected List<ProcessedSample> samples;
    protected MemoryFileStorage ms2Storage;
    protected AtomicInteger numberOfMs2Scans = new AtomicInteger();
    protected volatile boolean centroided = true;

    protected Set<PrecursorIonType> detectableIonTypes;

    public LCMSProccessingInstance() {
        this.samples = new ArrayList<>();
        try {
            this.ms2Storage = new MemoryFileStorage();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        this.storages = new HashMap<ProcessedSample, SpectrumStorage>();
        this.detectableIonTypes = new HashSet<>(Arrays.asList(
                PrecursorIonType.fromString("[M+Na]+"),
                PrecursorIonType.fromString("[M+K]+"),
                PrecursorIonType.fromString("[M+H]+"),
                PrecursorIonType.fromString("[M-H2O+H]+"),
                PrecursorIonType.fromString("[M-H4O2+H]+"),
                PrecursorIonType.fromString("[M-H2O+Na]+"),
                PrecursorIonType.fromString("[M+NH3+H]+"),
                PrecursorIonType.fromString("[M-H]-"),
                PrecursorIonType.fromString("[M+Cl]-"),
                PrecursorIonType.fromString("[M+Br]-"),
                PrecursorIonType.fromString("[M-H2O-H]-")
        ));
    }

    public CoelutingTraceSet getTraceset(ProcessedSample sample, FragmentedIon ion) {
        return new TraceConverter(sample, ion).asLCMSSubtrace();
    }

    public Set<PrecursorIonType> getDetectableIonTypes() {
        return detectableIonTypes;
    }

    public void setDetectableIonTypes(Set<PrecursorIonType> detectableIonTypes) {
        this.detectableIonTypes = detectableIonTypes;
    }

    public MemoryFileStorage getMs2Storage() {
        return ms2Storage;
    }

    public FragmentedIon createMs2Ion(ProcessedSample sample, MergedSpectrum merged, MutableChromatographicPeak peak, ChromatographicPeak.Segment segment) {
        final int id = numberOfMs2Scans.incrementAndGet();
        final SimpleSpectrum spec = merged.finishMerging();
        final SimpleSpectrum spec2 = Spectrums.extractMostIntensivePeaks(spec, 8, 100);
        final Scan scan = new Scan(id, merged.getScans().get(0).getPolarity(),peak.getRetentionTimeAt(segment.getApexIndex()), merged.getScans().get(0).getCollisionEnergy(),spec.size(), Spectrums.calculateTIC(spec), true, merged.getPrecursor());
        ms2Storage.add(scan, spec);
        final FragmentedIon ion = new FragmentedIon(merged.getScans().get(0).getPolarity(), scan, new CosineQueryUtils(new IntensityWeightedSpectralAlignment(new Deviation(20))).createQueryWithIntensityTransformationNoLoss(spec2, merged.getPrecursor().getMass(), true), merged.getQuality(spec), peak, segment, merged.getScans().toArray(Scan[]::new));
        return ion;
    }

    /**
     * has to be called after alignment
     */
    public IonNetwork detectAdductsWithGibbsSampling(Cluster alignedFeatures) {
        final IonNetwork network = new IonNetwork();
        for (AlignedFeatures features : alignedFeatures.getFeatures()) {
            network.addNode(features);
        }
        network.addCorrelatedEdgesForAllNodes();
        final ArrayList<AlignedFeatures> features = new ArrayList<>();
        network.gibbsSampling((feature,types,prob)->{
            for (FragmentedIon ion : feature.getFeatures().values()) {
                // we only consider adducts which are at least 1/5 as likely as the most likely option
                final double threshold = Arrays.stream(prob).max().orElse(0d)/5d;
                final HashSet<PrecursorIonType> set = new HashSet<>();
                boolean unknown = false;
                for (int k=0; k < types.length; ++k) {
                    if (types[k].isIonizationUnknown()) {
                        if (prob[k]>=threshold)
                            unknown=true;
                    } else {
                        if (prob[k] >= threshold) {
                            set.add(types[k]);
                        }
                    }
                }
                ion.setPossibleAdductTypes(set);
                if (!unknown && set.size()==1) ion.setDetectedIonType(set.iterator().next());
                else ion.setDetectedIonType(PrecursorIonType.unknown(ion.getPolarity()));
            }
        });
        return network;
    }

    public boolean isCentroided() {
        return centroided;
    }

    public ProcessedSample addSample(LCMSRun run, SpectrumStorage storage) throws InvalidInputData {
        final NoiseStatistics noiseStatisticsMs1 = new NoiseStatistics(100, 0.2, 1000)/*, noiseStatisticsMs2 = new NoiseStatistics(10, 0.85, 60)*/;

        final Ms2NoiseStatistics ms2NoiseStatistics = new Ms2NoiseStatistics();

        boolean hasMsMs = false;
        for (Scan s : run.getScans()) {
            if (!s.isCentroided()) {
                this.centroided = false;
                LoggerFactory.getLogger(LCMSProccessingInstance.class).warn("Scan " + s + " is in PROFILED mode. SIRIUS does only support centroided spectra. Ignore this scan.");
                continue;
            }
            if (s.isMsMs()) {
                hasMsMs = true;
                //noiseStatisticsMs2.add(s, storage.getScan(s));
                ms2NoiseStatistics.add(s,storage.getScan(s));
            } else {
                noiseStatisticsMs1.add(s,storage.getScan(s));
            }
        }

        if (!hasMsMs) throw new InvalidInputData("Run has no MS/MS spectra.");

        ms2NoiseStatistics.done();

        final ProcessedSample sample = new ProcessedSample(
                run, noiseStatisticsMs1.getLocalNoiseModel(), ms2NoiseStatistics,
                new ChromatogramCache(), storage
        );
        synchronized (this) {
            this.samples.add(sample);
            this.storages.put(sample, storage);
        }
        return sample;
    }

    public Feature makeFeature(ProcessedSample sample, FragmentedIon ion, boolean gapFilled) {
        int charge = ion.getChargeState();
        if (charge == 0) {
            if (ion.getMsMsScan()!=null && ion.getMsMsScan().getPolarity()!=null) {
                charge = ion.getMsMsScan().getPolarity().charge;
            } else {
                //LoggerFactory.getLogger(LCMSProccessingInstance.class).warn("Unknown polarity. Set polarity to POSITIVE");
                charge = 1;
            }
        }
        CollisionEnergy collisionEnergy;
        if (ion.getMsMsScan()!=null){
            collisionEnergy= new CollisionEnergy(ion.getMsMsScan().getCollisionEnergy(),ion.getMsMsScan().getCollisionEnergy());
        }else {
            collisionEnergy= null;
        }


        final double ionMass;
        {
            int a = ion.getSegment().getFwhmStartIndex(), b = ion.getSegment().getFwhmEndIndex();
            double mz = 0d, intens = 0d;
            for (int i=a; i <= b; ++i) {
                ScanPoint p = ion.getPeak().getScanPointAt(i);
                mz += p.getMass()*p.getIntensity();
                intens += p.getIntensity();
            }
            mz /= intens;
            ionMass = mz;
        }

        // TODO: mal nachlesen wie man am sinnvollsten quanifiziert
        final double intensity = ion.getPeak().getIntensityAt(ion.getSegment().getApexIndex());

        final ArrayList<SimpleSpectrum> correlatedFeatures = new ArrayList<>();
        {
            final SimpleMutableSpectrum isotope = toIsotopeSpectrum(ion, ionMass);

            {
                if (isotope.size() <= 2 && ion.getMsQuality().betterThan(Quality.DECENT)) {
                    System.err.println("SOMETHING STRANGE IS HAPPENING WITH " + ion);
                    System.err.println("-------------");
                    System.out.println(ion.getIsotopes().get(0).getLeftSegment().getApexMass() + "\t"  + ion.getIsotopes().get(0).getLeftSegment().getApexIntensity());
                    for (CorrelationGroup g : ion.getIsotopes()) {
                        System.out.println(g.getRightSegment().getApexMass() + "\t"  + g.getRightSegment().getApexIntensity());
                    }
                    System.err.println("-------------");
                }
            }

            correlatedFeatures.add(new SimpleSpectrum(isotope));
            for (CorrelatedIon adduct : ion.getAdducts()) {
                correlatedFeatures.add(new SimpleSpectrum(toIsotopeSpectrum(adduct.ion, adduct.ion.getPeak().getMzAt(adduct.ion.getPeak().findScanNumber(ion.getSegment().getApexScanNumber())))));
            }

        }

        PrecursorIonType ionType = PrecursorIonType.unknown(charge);
        if (ion.getDetectedIonType()!=null) {
            ionType = ion.getDetectedIonType();
        }

        if (ion.getPeakShape()==null)
            fitPeakShape(sample,ion);


        final Feature feature = new Feature(sample.run, ionMass, intensity, getTraceset(sample,ion), correlatedFeatures.toArray(new SimpleSpectrum[0]), 0,ion.getMsMsScan()==null ? new SimpleSpectrum[0] : new SimpleSpectrum[]{ms2Storage.getScan(ion.getMsMsScan())},sample.ms2NoiseInformation,collisionEnergy, ionType, ion.getPossibleAdductTypes(), sample.recalibrationFunction,
                ion.getPeakShape().getPeakShapeQuality(), ion.getMsQuality(), ion.getMsMsQuality(),ion.getChimericPollution()

                );
        feature.setAnnotation(PeakShape.class, fitPeakShape(sample,ion));
        return feature;
    }

    @NotNull
    public static SimpleMutableSpectrum toIsotopeSpectrum(IonGroup ion, double ionMass) {
        final SimpleMutableSpectrum isotope = new SimpleMutableSpectrum();
        isotope.addPeak(ionMass, 1.0d);
        eachPeak:
        for (CorrelationGroup iso : ion.getIsotopes()) {
            final ChromatographicPeak l = iso.getLeft();
            final ChromatographicPeak r = iso.getRight();
            final ChromatographicPeak.Segment s = iso.getRightSegment();
            double ratios = 0d, mzs = 0d,intens=0d;
            int a = s.getFwhmStartIndex(); int b = s.getFwhmEndIndex(); int n = b-a+1;
            for (; a <= b; ++a) {
                double rInt = r.getIntensityAt(a);
                int iL = l.findScanNumber(r.getScanNumberAt(a));
                if (iL < 0) {
                    LoggerFactory.getLogger(LCMSProccessingInstance.class).warn("Strange isotope peak picked for feature " + ion);
                    break eachPeak;
                }
                ratios += rInt / l.getScanPointAt(iL).getIntensity();
                mzs +=  (r.getIntensityAt(a)*r.getMzAt(a));
                intens += r.getIntensityAt(a);
            }
            isotope.addPeak(mzs/intens, ratios/n);
        }
        return isotope;
    }

    public void detectFeatures(ProcessedSample sample) {
        final List<FragmentedIon> ions = new Ms2CosineSegmenter().extractMsMSAndSegmentChromatograms(this, sample);
        ////
        sample.ions.clear(); sample.ions.addAll(ions);
        assert checkForDuplicates(sample);
        ////
        {
            final double[] intensityAfterPrec = new double[sample.ions.size()];
            int n=0;
            for (int k=0; k < sample.ions.size(); ++k) {
                if (sample.ions.get(k).getMsMsQuality().betterThan(Quality.BAD)) {
                    intensityAfterPrec[k] = sample.ions.get(k).getIntensityAfterPrecursor();
                    ++n;
                }
            }
            Arrays.sort(intensityAfterPrec,0,n);

            int k=n/2;
            while (k < n && intensityAfterPrec[k] <= 0) {
                ++k;
            }

            if (k>=n) {
                sample.intensityAfterPrecursorDistribution = null;
            } else {
                sample.intensityAfterPrecursorDistribution = ExponentialDistribution.getMedianEstimator().extimateByMedian(intensityAfterPrec[k]);
                LoggerFactory.getLogger(LCMSProccessingInstance.class).info("Median intensity after precursor in MS/MS: " + intensityAfterPrec[k]);
            }
        }
        ListIterator<FragmentedIon> iter = ions.listIterator();
        final CorrelatedPeakDetector detector = new CorrelatedPeakDetector(detectableIonTypes);
        while (iter.hasNext()) {
            final FragmentedIon ion = iter.next();
            if (!detector.detectCorrelatedPeaks(sample, ion))
                iter.remove();
        }
        assert checkForDuplicates(sample);
        sample.ions.clear();
        sample.ions.addAll(ions);
        /*
        sample.ions.clear();
        sample.ions.addAll(new IonIdentityNetwork().filterByIonIdentity(ions));
        assert checkForDuplicates(sample);
         */


        TDoubleArrayList peakWidths = new TDoubleArrayList(),peakWidthsToHeight = new TDoubleArrayList();
        for (FragmentedIon f : sample.ions) {
            final long fwhm = f.getSegment().fwhm(0.2);
            peakWidths.add(fwhm);
            peakWidthsToHeight.add(fwhm/f.getIntensity());
        }
        peakWidths.sort();
        peakWidthsToHeight.sort();
        sample.meanPeakWidth = Statistics.robustAverage(peakWidths.toArray());
        sample.meanPeakWidthToHeightRatio = Statistics.robustAverage(peakWidthsToHeight.toArray());
        for (int k=0; k < peakWidthsToHeight.size(); ++k) {
            peakWidthsToHeight.set(k, Math.pow(peakWidthsToHeight.get(k)-sample.meanPeakWidthToHeightRatio,2));
        }
        sample.meanPeakWidthToHeightRatioStd = Math.sqrt(Statistics.robustAverage(peakWidthsToHeight.toArray()));
        for (FragmentedIon ion : ions) {
            fitPeakShape(sample, ion);
        }
    }

    public PeakShape fitPeakShape(ProcessedSample sample, FragmentedIon ion) {
        /*
        final GaussianShape gaus = new GaussianFitting().fit(sample, ion.getPeak(), ion.getSegment());
        final LaplaceShape laplace = new LaplaceFitting().fit(sample, ion.getPeak(), ion.getSegment());
        if (gaus.getScore()>laplace.getScore()) {
            ion.setPeakShape(gaus);
            return gaus;
        } else {
            ion.setPeakShape(laplace);
            return laplace;
        }
         */
        final CustomPeakShape fit = new CustomPeakShapeFitting().fit(sample, ion.getPeak(), ion.getSegment());
        ion.setPeakShape(fit);
        return fit;
    }

    /**
     *
     */
    void addAllSegmentsAsPseudoIons() {
        for (ProcessedSample sample : samples) {
            final HashSet<ChromatographicPeak.Segment> allSegments = new HashSet<ChromatographicPeak.Segment>();
            for (FragmentedIon ion : sample.ions) {
                allSegments.add(ion.getSegment());
            }
            for (FragmentedIon ion : sample.gapFilledIons) {
                allSegments.add(ion.getSegment());
            }
            for (FragmentedIon ion : sample.ions) {
                for (ChromatographicPeak.Segment s : ion.getPeak().getSegments()) {
                    if (!allSegments.contains(s)) {
                        final GapFilledIon newIon = new GapFilledIon(Polarity.of(ion.getPolarity()), ion.getPeak(), s, ion);
                        fitPeakShape(sample, newIon);
                        sample.gapFilledIons.add(newIon);
                        allSegments.add(s);
                    }
                }
            }
        }
    }

    public void detectFeatures() {
        for (ProcessedSample sample : samples) {
            detectFeatures(sample);
        }
    }

    public Cluster alignAndGapFilling() {
        return alignAndGapFilling(null);
    }

    public Cluster alignAndGapFilling(ProgressJJob<?> jobWithProgress) {
        final int maxProgress = 7;
        int currentProgress = 0;
        JobManager manager = SiriusJobs.getGlobalJobManager();
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Estimate retention time shifts between samples");
        boolean similarRt = true;
        int numberOfUnalignedIons = 0;
        double maxRt = samples.stream().mapToDouble(x->x.maxRT).max().getAsDouble();
        for (ProcessedSample s : samples) {
            s.maxRT = maxRt;
            numberOfUnalignedIons += s.ions.size();
        }
        double error = new Aligner(false).estimateErrorTerm(samples);
        System.out.println("ERROR = " + error);

        final double initialError = error;
        int n=0;
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Filter data: remove ions with low quality MS/MS spectrum and MS1 peak");
        System.out.println("Start with " + numberOfUnalignedIons + " unaligned ions.");
        System.out.println("Remove features with low MS/MS quality that do not align properly");System.out.flush();
        int deleted = manager.submitJob(new Aligner(false).prealignAndFeatureCutoff2(samples, 15*error, 1)).takeResult();
        System.out.println("Remove " + deleted + " features that do not align well. Keep " + (numberOfUnalignedIons-deleted) + " features." );

        addAllSegmentsAsPseudoIons();
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Start first alignment ");
        BasicJJob<Cluster> clusterJob = new Aligner2(error*5).align(samples);//new Aligner().recalibrateRetentionTimes(this.samples);
        manager.submitJob(clusterJob);
        Cluster cluster = clusterJob.takeResult();
        error = cluster.estimateError();
        final double errorFromClustering = error;
        clusterJob = new GapFilling().gapFillingInParallel(this, cluster.deleteRowsWithNoMsMs(), error,cluster.estimatePeakShapeError(), true);
        manager.submitJob(clusterJob);
        cluster = clusterJob.takeResult();
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Estimate parameters and start second alignment");
        clusterJob = new Aligner2(error).align(samples);
        manager.submitJob(clusterJob);
        cluster = clusterJob.takeResult();
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Recalibrate retention times");
        manager.submitJob(new Aligner(false).recalibrateRetentionTimes(samples, cluster, error)).takeResult();
        error = cluster.estimateError();
        final double errorDueToRecalibration = error;
        addAllSegmentsAsPseudoIons();
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Start third alignment");
        clusterJob  = new Aligner2(error).align(samples);
        manager.submitJob(clusterJob);
        cluster = clusterJob.takeResult().deleteRowsWithNoMsMs();
        System.out.println("Start Gapfilling #2");System.out.flush();
        clusterJob = new GapFilling().gapFillingInParallel(this, cluster, error, cluster.estimatePeakShapeError(), false);
        manager.submitJob(clusterJob);
        cluster = clusterJob.takeResult();

        final double finalError = cluster.estimateError();

        System.out.println("########################################");
        System.out.println("Initial Error: " + initialError);
        System.out.println("After clustering: " + errorFromClustering);
        System.out.println("After Recalibration: " + errorDueToRecalibration);
        System.out.println("After Gap-Filling: " + finalError);
        System.out.println("PeakShape Error: " + cluster.estimatePeakShapeError());
        if (jobWithProgress!=null) jobWithProgress.updateProgress(0, maxProgress, currentProgress++, "Start final alignment");
        cluster = manager.submitJob(new Aligner2(error).align(samples)).takeResult();
        double numberOfFeatures = cluster.getFeatures().length;
        cluster = cluster.deleteRowsWithNoMsMs();
        cluster = cluster.deleteRowsWithNoIsotopes();
        double numberOfFeatures2 = cluster.getFeatures().length;
        System.out.println("Remove " + (100d - 100d*numberOfFeatures2/numberOfFeatures ) +  " % of the data due to low quality. There are " + cluster.getFeatures().length + " features in total."); System.out.flush();
        if (samples.size()>=50) cluster = cluster.deleteRowsWithTooFewEntries(4);
        int after = cluster.getFeatures().length;
        System.out.println("Done."); System.out.flush();
        System.out.println("Total number of features is " + cluster.getFeatures().length);
        return cluster;


    }


    private boolean checkForDuplicates(Cluster cluster) {
        final HashSet<ChromatographicPeak.Segment> alLSegments = new HashSet<>();
        for (AlignedFeatures al  : cluster.getFeatures()) {
            for (FragmentedIon I : al.getFeatures().values()) {
                if (!alLSegments.add(I.getSegment())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkForDuplicates(ProcessedSample sample) {
        final HashMap<ChromatographicPeak.Segment, FragmentedIon> set = new HashMap<>();
        for (FragmentedIon ion : sample.ions) {
            if (set.putIfAbsent(ion.getSegment(), ion)!=null)
                return false;
        }
        for (FragmentedIon ion : sample.gapFilledIons) {
            if (set.putIfAbsent(ion.getSegment(), ion)!=null)
                return false;
        }
        return true;
    }

    public ConsensusFeature[] makeConsensusFeatures(Cluster cluster) {
        return new Aligner(false).makeFeatureTable(this, cluster);
    }


    public List<ProcessedSample> getSamples() {
        return samples;
    }

    public SimpleSpectrum getMs2(Scan msMsScan) {
        return ms2Storage.getScan(msMsScan);
    }
}
