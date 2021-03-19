

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

package de.unijena.bioinf.ms.rest.model.fingerid;

import de.unijena.bioinf.ms.rest.model.JobState;
import de.unijena.bioinf.ms.rest.model.JobTable;
import de.unijena.bioinf.ms.rest.model.JobWithPredictor;

public class SiriusPredictionJob extends JobWithPredictor<FingerprintJobOutput> {
    protected String ms, jsonTree;
    protected byte[] fingerprint; // LITTLE ENDIAN BINARY ENCODED PLATT PROBABILITIES
    protected byte[] iokrVector; // LITTLE ENDIAN BINARY ENCODED PLATT PROBABILITIES

    public SiriusPredictionJob() {
        this(null, null, null, null);
    }

    public SiriusPredictionJob(String workerPrefix, Long predictors, Long lockedByWorker) {
        this(workerPrefix, null, null, predictors);
        setLockedByWorker(lockedByWorker);
    }

    public SiriusPredictionJob(String workerPrefix, Long jobId, JobState state, Long predictors) {
        super(workerPrefix, jobId, state, JobTable.JOBS_FINGERID);
        this.predictors = predictors;
    }


    public byte[] getFingerprint() {
        return fingerprint;
    }

    public byte[] getIokrVector() {
        return iokrVector;
    }

    public void setIokrVector(byte[] iokrVector) {
        this.iokrVector = iokrVector;
    }

    public void setFingerprint(byte[] fingerprints) {
        this.fingerprint = fingerprints;
    }

    public String getMs() {
        return ms;
    }

    public void setMs(String ms) {
        this.ms = ms;
    }

    public String getJsonTree() {
        return jsonTree;
    }

    public void setJsonTree(String jsonTree) {
        this.jsonTree = jsonTree;
    }

    @Override
    public FingerprintJobOutput extractOutput() {
        return new FingerprintJobOutput(fingerprint, iokrVector);
    }
}
