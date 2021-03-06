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

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.fp.ProbabilityFingerprint;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.chemdb.FingerprintCandidate;
import de.unijena.bioinf.confidence_score.ConfidenceScorer;
import de.unijena.bioinf.fingerid.blast.*;
import de.unijena.bioinf.jjobs.BasicDependentMasterJJob;
import de.unijena.bioinf.jjobs.JJob;
import de.unijena.bioinf.ms.annotations.AnnotationJJob;
import de.unijena.bioinf.sirius.IdentificationResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by martin on 08.08.18.
 */
public class ConfidenceJJob extends BasicDependentMasterJJob<ConfidenceResult> implements AnnotationJJob<ConfidenceResult, FingerIdResult> {

    //fina inputs
    protected final ConfidenceScorer confidenceScorer;
    protected final Ms2Experiment experiment;
    protected IdentificationResult<?> siriusidresult;

    protected final Set<FingerblastJJob> inputInstances = new LinkedHashSet<>();
    protected final ScoringMethodFactory.CSIFingerIdScoringMethod csiScoring;


    //INPUT
    // puchem resultlist
    // filterflag oder filtered list
    // ConfidenceScoreComputer
    // Scorings: CovarianceScoring, CSIFingerIDScoring (reuse)
    // IdentificationResult
    // Experiment -> CollisionEnergies


    //OUTPUT
    // ConfidenceResult -> Annotate to

    public ConfidenceJJob(@NotNull CSIPredictor predictor, Ms2Experiment experiment) {
        super(JobType.CPU);
        this.confidenceScorer = predictor.getConfidenceScorer();
        this.csiScoring = new ScoringMethodFactory.CSIFingerIdScoringMethod(predictor.performances);
        this.experiment = experiment;

    }


    @Override
    public synchronized void handleFinishedRequiredJob(JJob required) {
        if (required instanceof FingerblastJJob) {
            final FingerblastJJob searchDBJob = (FingerblastJJob) required;
            if (searchDBJob.result() != null && searchDBJob.result().getTopHitScore() != null) {
                inputInstances.add(searchDBJob);
            } else {
                if (searchDBJob.result() == null)
                    LoggerFactory.getLogger(getClass()).warn("Fingerblast Job '" + searchDBJob.identifier() + "' skipped because of result was null.");
            }
        }
    }


    @Override
    protected ConfidenceResult compute() throws Exception {

        checkForInterruption();


        Map<FingerblastJJob, List<JJob<List<Scored<FingerprintCandidate>>>>> csiScoreJobs = new HashMap<>();


        final List<Scored<FingerprintCandidate>> allMergedCandidatesCov = new ArrayList<>();
        final List<Scored<FingerprintCandidate>> allMergedCandidatesCSI = new ArrayList<>();
        final List<Scored<FingerprintCandidate>> requestedMergedCandidatesCov = new ArrayList<>();
        final List<Scored<FingerprintCandidate>> requestedMergedCandidatesCSI = new ArrayList<>();


        Double topHitScore = null;
        ProbabilityFingerprint topHitFP = null;
        FTree topHitTree = null;
        MolecularFormula topHitFormula = null;
//        BayesnetScoring topHitScoring = null;

        for (FingerblastJJob searchDBJob : inputInstances) {
            FingerblastResult r = searchDBJob.result();
            final List<Scored<FingerprintCandidate>> allRestDbScoredCandidates = searchDBJob.getCandidates().getAllDbCandidatesInChIs().map(set ->
                    searchDBJob.getAllScoredCandidates().stream().filter(sc -> set.contains(sc.getCandidate().getInchiKey2D())).collect(Collectors.toList())).
                    orElseThrow(() -> new IllegalArgumentException("Additional candidates Flag 'ALL' from DataSource is not Available but mandatory to compute Confidence scores!"));


            allMergedCandidatesCov.addAll(allRestDbScoredCandidates);
            requestedMergedCandidatesCov.addAll(r.getResults());

            if (topHitScore == null || topHitScore < r.getTopHitScore().score()) {
                topHitScore = r.getTopHitScore().score();
                topHitFP = searchDBJob.fp;
//                topHitTree = searchDBJob.ftree;
//                topHitFormula = searchDBJob.formula;
//                topHitScoring = searchDBJob.bayesnetScoring;
            }

            // build csi scoring jobs
//            csiScoring.getScoring().

//            CSIFingerIdScoring scoring = csiScoring.getScoring();
//            scoring.prepare(searchDBJob.fp);

            List<JJob<List<Scored<FingerprintCandidate>>>> j = Fingerblast.makeScoringJobs(csiScoring,
                    allRestDbScoredCandidates.stream().map(SScored::getCandidate).collect(Collectors.toList()),
                    searchDBJob.fp);
            csiScoreJobs.put(searchDBJob, j);
            j.forEach(this::submitSubJob);
        }

        checkForInterruption();

        csiScoreJobs.forEach((k,v) -> {
            Set<String> filterSet = k.result().getResults().stream().map(SScored::getCandidate).map(FingerprintCandidate::getInchiKey2D).collect(Collectors.toSet());
            List<Scored<FingerprintCandidate>> allCSI = v.stream().map(JJob::takeResult).flatMap(Collection::stream).collect(Collectors.toList());
            List<Scored<FingerprintCandidate>> requestCSI = allCSI.stream().filter(c -> filterSet.contains(c.getCandidate().getInchiKey2D())).collect(Collectors.toList());
            allMergedCandidatesCSI.addAll(allCSI);
            requestedMergedCandidatesCSI.addAll(requestCSI);
        });

        checkForInterruption();

        csiScoreJobs.clear();
        inputInstances.clear();

        allMergedCandidatesCov.sort(Comparator.reverseOrder());
        requestedMergedCandidatesCov.sort(Comparator.reverseOrder());

        allMergedCandidatesCSI.sort(Comparator.reverseOrder());
        requestedMergedCandidatesCSI.sort(Comparator.reverseOrder());

        assert  allMergedCandidatesCov.size() == allMergedCandidatesCSI.size();
        assert  requestedMergedCandidatesCov.size() == requestedMergedCandidatesCSI.size();

        checkForInterruption();

        //todo find tophit id result
        final double score = confidenceScorer.computeConfidence(experiment, siriusidresult,
                allMergedCandidatesCov, allMergedCandidatesCSI,
                requestedMergedCandidatesCov, requestedMergedCandidatesCSI, topHitFP);

        checkForInterruption();
        return new ConfidenceResult(score, requestedMergedCandidatesCov.size() > 0 ? requestedMergedCandidatesCov.get(0) : null);
    }

}
