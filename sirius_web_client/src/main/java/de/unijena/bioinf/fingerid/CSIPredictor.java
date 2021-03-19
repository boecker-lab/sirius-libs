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

package de.unijena.bioinf.fingerid;

import de.unijena.bioinf.ChemistryBase.fp.MaskedFingerprintVersion;
import de.unijena.bioinf.ChemistryBase.fp.PredictionPerformance;
import de.unijena.bioinf.confidence_score.CSICovarianceConfidenceScorer;
import de.unijena.bioinf.confidence_score.svm.TrainedSVM;
import de.unijena.bioinf.fingerid.blast.BayesnetScoring;
import de.unijena.bioinf.fingerid.blast.BayesnetScoringWithDynamicComputation;
import de.unijena.bioinf.fingerid.blast.FingerblastScoring;
import de.unijena.bioinf.fingerid.blast.ScoringMethodFactory;
import de.unijena.bioinf.fingerid.blast.parameters.Parameters;
import de.unijena.bioinf.fingerid.predictor_types.PredictorType;
import de.unijena.bioinf.fingerid.predictor_types.UserDefineablePredictorType;
import de.unijena.bioinf.ms.rest.model.fingerid.FingerIdData;
import de.unijena.bioinf.webapi.WebAPI;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * This is the API class for CSI:FingerID and Fingerblast.
 * We init a separate predictor object for positive and negative ionization
 */
public class CSIPredictor extends AbstractStructurePredictor<Parameters.UnpreparedScoring<BayesnetScoring,Parameters.FP>> {
    protected MaskedFingerprintVersion fpVersion;
    protected PredictionPerformance[] performances;
    protected volatile boolean initialized;

    public CSIPredictor(PredictorType predictorType, WebAPI api) {
        super(predictorType, api);
        if (!UserDefineablePredictorType.CSI_FINGERID.contains(predictorType))
            throw new IllegalArgumentException("Illegal Predicortype for this object. CSI:FingerID positive and negative only.");
    }

    @Override
    public FingerblastScoring<?> getPreparedFingerblastScorer(Parameters.UnpreparedScoring<BayesnetScoring,Parameters.FP> parameters) {
        /*if(!initialized) {
            initialize();
        }*/
        BayesnetScoringWithDynamicComputation scorer = (BayesnetScoringWithDynamicComputation) fingerblastScoring.getScoring();
        scorer.prepare(parameters);
        return scorer;
    }

    public MaskedFingerprintVersion getFingerprintVersion() {
        return fpVersion;
    }

    public PredictionPerformance[] getPerformances() {
        return performances;
    }


    @Override
    public void refreshCacheDir() throws IOException {
        database = csiWebAPI.getChemDB();
        database.checkCache();
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }

    public synchronized void initialize() throws IOException {
        if (initialized)
            throw new IllegalStateException("Predictor is already initialized"); //maybe just skip

        final FingerIdData fingeridData = csiWebAPI.getFingerIdData(predictorType);
        performances = fingeridData.getPerformances();
        fpVersion = fingeridData.getFingerprintVersion();

        fingerblastScoring = new ScoringMethodFactory.BayesnetScoringWithDynamicComputationScoringMethod();
        confidenceScorer = makeConfidenceScorer();

        trainingStructures = TrainingStructuresPerPredictor.getInstance().getTrainingStructuresSet(predictorType, csiWebAPI);
        refreshCacheDir();
        initialized = true;
    }

    private CSICovarianceConfidenceScorer makeConfidenceScorer() {
        try {
            final Map<String, TrainedSVM> confidenceSVMs = csiWebAPI.getTrainedConfidence(predictorType);
            if (confidenceSVMs == null || confidenceSVMs.isEmpty())
                throw new IOException("WebAPI returned empty confidence SVMs");

            final BayesnetScoring cvs = csiWebAPI.getBayesnetScoring(predictorType);
            if(cvs == null)
                throw new IOException(("WebAPI returned no default bayesian network."));

            final ScoringMethodFactory.CSIFingerIdScoringMethod csiScoring = new ScoringMethodFactory.CSIFingerIdScoringMethod(performances);

            return new CSICovarianceConfidenceScorer(confidenceSVMs, cvs, csiScoring, fingerblastScoring.getClass());
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("Error when loading confidence SVMs or the bayesian network. Confidence SCore will not be available!");
            LoggerFactory.getLogger(getClass()).debug("Error when loading confidence SVMs or the bayesian network.", e);
            return null;
        }
    }
}
