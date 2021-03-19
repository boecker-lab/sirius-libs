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

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.fp.ProbabilityFingerprint;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.chemdb.CompoundCandidate;
import de.unijena.bioinf.chemdb.DataSource;
import de.unijena.bioinf.chemdb.FingerprintCandidate;
import de.unijena.bioinf.chemdb.RestWithCustomDatabase;
import de.unijena.bioinf.fingerid.blast.BayesnetScoring;
import de.unijena.bioinf.fingerid.blast.Fingerblast;
import de.unijena.bioinf.fingerid.blast.FingerblastResult;
import de.unijena.bioinf.fingerid.blast.FingerblastScoring;
import de.unijena.bioinf.fingerid.blast.parameters.BayesnetDynamicParameters;
import de.unijena.bioinf.jjobs.JJob;
import de.unijena.bioinf.ms.annotations.AnnotationJJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FingerblastJJob extends FingerprintDependentJJob<FingerblastResult> implements AnnotationJJob<FingerblastResult, FingerIdResult> {

    private final CSIPredictor predictor;

    private BayesnetScoring bayesnetScoring = null;
    private RestWithCustomDatabase.CandidateResult candidates = null;
    private List<Scored<FingerprintCandidate>> scoredCandidates = null;

    public FingerblastJJob(@NotNull CSIPredictor predictor) {
        this(predictor, null, null, null);
    }

    public FingerblastJJob(@NotNull CSIPredictor predictor, @NotNull BayesnetScoring bayesnetScoring){
        this(predictor);
        this.bayesnetScoring = bayesnetScoring;
    }

    public FingerblastJJob(@NotNull CSIPredictor predictor, FTree tree, ProbabilityFingerprint fp, MolecularFormula formula) {
        super(JobType.CPU, fp, formula, tree);
        this.predictor = predictor;
    }


    protected void checkInput() {
        if (candidates == null)
            throw new IllegalArgumentException("No Input Data found.");
    }

    @Override
    public synchronized void handleFinishedRequiredJob(JJob required) {
        super.handleFinishedRequiredJob(required);
        if (candidates == null) {
            if (required instanceof FormulaJob) {
                FormulaJob job = ((FormulaJob) required);
                candidates = job.result();
            }
        }
        if(bayesnetScoring == null){
            if(required instanceof CovtreeWebJJob){
                CovtreeWebJJob job = (CovtreeWebJJob) required;
                bayesnetScoring = job.result();
            }
        }
    }

    public List<Scored<FingerprintCandidate>> getAllScoredCandidates() {
        return scoredCandidates;
    }

    public RestWithCustomDatabase.CandidateResult getCandidates() {
        return candidates;
    }

    @Override
    protected FingerblastResult compute() {
        checkInput();
        //we want to score all available candidates and may create subsets later.
        final Set<FingerprintCandidate> combinedCandidates = candidates.getCombCandidates();

        // to get a prepared FingerblastScorer, an object of BayesnetScoring that is specific to the molecular formula has to be initialized
        List<JJob<List<Scored<FingerprintCandidate>>>> scoreJobs = Fingerblast.makeScoringJobs(
                predictor.getPreparedFingerblastScorer(new BayesnetDynamicParameters(fp, bayesnetScoring)), combinedCandidates, fp);
        scoreJobs.forEach(this::submitSubJob);

        scoredCandidates = scoreJobs.stream().flatMap(r -> r.takeResult().stream()).sorted(Comparator.reverseOrder()).map(fpc -> new Scored<>(fpc.getCandidate(), fpc.getScore())).collect(Collectors.toList());
        scoredCandidates.forEach(sc -> postprocessCandidate(sc.getCandidate()));

        //create filtered result for FingerblastResult result
        Set<String> requestedCandidatesInChIs = candidates.getReqCandidatesInChIs();
        final List<Scored<FingerprintCandidate>> cds = scoredCandidates.stream().
                filter(sc -> requestedCandidatesInChIs.contains(sc.getCandidate().getInchiKey2D())).collect(Collectors.toList());

        return new FingerblastResult(cds);
    }

    protected void postprocessCandidate(CompoundCandidate candidate) {
        //annotate training compounds;
        if (predictor.getTrainingStructures().isInTrainingData(candidate.getInchi())){
            long flags = candidate.getBitset();
            candidate.setBitset(flags | DataSource.TRAIN.flag);
        }
    }
}
