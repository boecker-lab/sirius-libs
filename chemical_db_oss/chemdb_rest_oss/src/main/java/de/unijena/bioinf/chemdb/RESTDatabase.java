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

package de.unijena.bioinf.chemdb;

import de.unijena.bioinf.ChemistryBase.chem.InChI;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Deviation;
import de.unijena.bioinf.auth.AuthService;
import de.unijena.bioinf.fingerid.utils.FingerIDProperties;
import de.unijena.bioinf.jjobs.Partition;
import de.unijena.bioinf.ms.rest.client.chemdb.ChemDBClient;
import de.unijena.bioinf.ms.rest.client.chemdb.StructureSearchClient;
import de.unijena.bioinf.storage.blob.BlobStorage;
import de.unijena.bioinf.storage.blob.file.FileBlobStorage;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RESTDatabase implements AbstractChemicalDatabase {
    static {
        FingerIDProperties.fingeridFullVersion();
    }

    private final OkHttpClient client;
    private final boolean closeCLient;

    private StructureSearchClient chemDBClient;
    protected final ChemDBFileCache cache;
    protected long filter;


    public static BlobStorage defaultCache() {
        final String val = System.getenv("CSI_FINGERID_STORAGE");
        if (val != null) return new FileBlobStorage(Path.of(val));
        return new FileBlobStorage(Path.of(System.getProperty("user.home"), "csi_fingerid_cache"));
    }

    @Override
    public String getChemDbDate() throws ChemicalDatabaseException {
        try {
            return chemDBClient.getChemDbDate(client);
        } catch (IOException e) {
            throw new ChemicalDatabaseException(e);
        }
    }

    public RESTDatabase(@Nullable BlobStorage cacheDir, long filter, @NotNull StructureSearchClient chemDBClient, @NotNull OkHttpClient client) {
        this(cacheDir, filter, chemDBClient, client, false);
    }

    public RESTDatabase(@Nullable BlobStorage cacheDir, long filter, @NotNull StructureSearchClient chemDBClient, @NotNull OkHttpClient client, boolean closeClient) {
        this.closeCLient = closeClient;
        this.filter = filter;
        this.chemDBClient = chemDBClient;
        this.client = client;
        this.cache = new ChemDBFileCache(cacheDir != null ? cacheDir : defaultCache(), new SearchStructureByFormula() {
            @Override
            public <T extends Collection<FingerprintCandidate>> T lookupStructuresAndFingerprintsByFormula(MolecularFormula formula, T fingerprintCandidates) throws ChemicalDatabaseException {
                try {
                    //get unfiltered list from server to write cache.
                    fingerprintCandidates.addAll(chemDBClient.getCompounds(formula, DataSource.ALL.flag(), client));
                    return fingerprintCandidates;
                } catch (IOException e) {
                    throw new ChemicalDatabaseException(e);
                }
            }
        });
    }

    // closes clients

    public RESTDatabase(long filter, @NotNull AuthService authService) {
        this(filter, URI.create(FingerIDProperties.siriusFallbackWebHost()), authService);
    }

    public RESTDatabase(long filter) {
        this(filter, URI.create(FingerIDProperties.siriusFallbackWebHost()));
    }

    public RESTDatabase(long filter, @NotNull URI serverURL) {
        this(RESTDatabase.defaultCache(), filter, new ChemDBClient(serverURL), new OkHttpClient.Builder().build(), true);
    }

    public RESTDatabase(long filter, @NotNull URI serverURL, @NotNull AuthService authService) {
        this(RESTDatabase.defaultCache(), filter, new ChemDBClient(serverURL, authService), new OkHttpClient.Builder().build(), true);
    }

    public RESTDatabase(long filter, @NotNull StructureSearchClient chemDBClient) {
        this(RESTDatabase.defaultCache(), filter, chemDBClient, new OkHttpClient.Builder().build(), true);
    }


    @Override
    public List<FormulaCandidate> lookupMolecularFormulas(double mass, Deviation deviation, PrecursorIonType ionType) throws ChemicalDatabaseException {
        try {
            return chemDBClient.getFormulas(mass, deviation, ionType, filter, client);
        } catch (IOException e) {
            throw new ChemicalDatabaseException(e);
        }
    }

    @Override
    public List<CompoundCandidate> lookupStructuresByFormula(MolecularFormula formula) throws ChemicalDatabaseException {
        final ArrayList<CompoundCandidate> candidates = new ArrayList<>();
        for (CompoundCandidate c : lookupStructuresAndFingerprintsByFormula(formula))
            candidates.add(new CompoundCandidate(c));
        return candidates;
    }

    @Override
    public <T extends Collection<FingerprintCandidate>> T lookupStructuresAndFingerprintsByFormula(MolecularFormula formula, T fingerprintCandidates) throws ChemicalDatabaseException {
        fingerprintCandidates.addAll(cache.lookupStructuresAndFingerprintsByFormula(formula, filter));
        return fingerprintCandidates;
    }


    @Override
    public List<FingerprintCandidate> lookupFingerprintsByInchis(Iterable<String> inchi_keys) throws ChemicalDatabaseException {
        if (chemDBClient instanceof ChemDBClient) {
            final Partition<String> keyParts = Partition.ofSize(inchi_keys, ChemDBClient.MAX_NUM_OF_INCHIS);
            final ArrayList<FingerprintCandidate> compounds = new ArrayList<>(keyParts.numberOfElements());

            try {
                for (List<String> inchiKeys : keyParts)
                    compounds.addAll(((ChemDBClient) chemDBClient).postCompounds(inchiKeys, client));
            } catch (IOException e) {
                throw new ChemicalDatabaseException(e);
            }
            return compounds;
        }
        throw new UnsupportedOperationException();
    }


    public Iterable<? extends FingerprintCandidate> lookupManyFingerprintsBy2dInchis(Collection<String> inchis2d) throws ChemicalDatabaseException {
        return lookupFingerprintsByInchis(inchis2d);
    }


    @Override
    public List<InChI> lookupManyInchisByInchiKeys(Iterable<String> inchi_keys) throws ChemicalDatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FingerprintCandidate> lookupManyFingerprintsByInchis(Iterable<String> inchi_keys) throws ChemicalDatabaseException {
        return lookupFingerprintsByInchis(inchi_keys);
    }

    @Override
    public List<FingerprintCandidate> lookupFingerprintsByInchi(Iterable<CompoundCandidate> compounds) throws ChemicalDatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void annotateCompounds(List<? extends CompoundCandidate> sublist) throws ChemicalDatabaseException {
        // already annotated
    }

    @Override
    public List<InChI> findInchiByNames(List<String> names) throws ChemicalDatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsFormula(MolecularFormula formula) throws ChemicalDatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (closeCLient && client != null) {
            client.connectionPool().evictAll();
            client.dispatcher().cancelAll();
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
