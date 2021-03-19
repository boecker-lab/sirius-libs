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

package de.unijena.bioinf.canopus;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.fp.*;
import de.unijena.bioinf.ChemistryBase.utils.FileUtils;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class Sampler {

    protected TDoubleArrayList[] positives, negatives, tps, fps, tns, fns;
    protected double[] recall, precision, truePositiveRate, falsePositiveRate, trueNegativeRate, falseNegativeRate;
    protected MaskedFingerprintVersion version;

    protected Pattern exclude,include;

    protected HashSet<String> trainInchiKeys;
    protected ProbabilityFingerprint[] trainFps;
    protected ArrayFingerprint[] perfectFps;
    protected ErrorStats[] errorStats;

    protected HashMap<String, Duplicate> structures;
    protected CovarianceTree covarianceTree;

    private static class ErrorStats {
        private double fpr, fnr, tanimoto;

        public ErrorStats(double fpr, double fnr, double tanimoto) {
            this.fpr = fpr;
            this.fnr = fnr;
            this.tanimoto = tanimoto;
        }
    }

    private static class Duplicate {
        private final String inchikey;
        private final TIntArrayList indizes;
        private final ArrayFingerprint perfectFingerprint;

        public Duplicate(String key, ArrayFingerprint perfectFingerprint) {
            this.indizes = new TIntArrayList();
            this.inchikey = key;
            this.perfectFingerprint = perfectFingerprint;
        }
    }

    public void buildCovarianceTree(File source) throws IOException {
        this.covarianceTree = new CovarianceTree(this, source);
    }

    public Pattern getExclude() {
        return exclude;
    }

    public void setExclude(Pattern exclude) {
        this.exclude = exclude;
    }

    public Pattern getInclude() {
        return include;
    }

    public void setInclude(Pattern include) {
        this.include = include;
    }

    public Sampler(MaskedFingerprintVersion version) {
        this.positives = new TDoubleArrayList[version.allowedIndizes().length];
        this.negatives = new TDoubleArrayList[version.allowedIndizes().length];
        this.tps = new TDoubleArrayList[version.size()];
        this.fps = new TDoubleArrayList[version.size()];
        this.tns = new TDoubleArrayList[version.size()];
        this.fns = new TDoubleArrayList[version.size()];
        this.recall = new double[version.size()];
        this.precision = new double[version.size()];
        for (int i = 0; i < version.size(); ++i) {
            positives[i] = new TDoubleArrayList();
            negatives[i] = new TDoubleArrayList();
            tps[i] = new TDoubleArrayList();
            fps[i] = new TDoubleArrayList();
            tns[i] = new TDoubleArrayList();
            fns[i] = new TDoubleArrayList();
        }
        truePositiveRate = new double[version.size()];
        falsePositiveRate = new double[version.size()];
        trueNegativeRate = new double[version.size()];
        falseNegativeRate = new double[version.size()];
        this.version = version;
        this.trainInchiKeys = new HashSet<>();
        this.structures = new HashMap<>();
    }


    public double[] sampleByErrorStats(ArrayFingerprint truth, int tries) {
        ErrorStats selected = null;
        Random r = new Random();
        for (int k=0; k  < tries; ++k) {
            ErrorStats s = errorStats[r.nextInt(errorStats.length)];
            if (selected==null || s.tanimoto < selected.tanimoto)
                selected = s;
        }
        // sampling
        final int positives = truth.cardinality();
        final int negatives = truth.getFingerprintVersion().size()-positives;

        // we expect
        double expectedFalsePositives = negatives*selected.fpr;
        double expectedFalseNegatives = positives*selected.fnr;
        int k=0;
        double fpsum = 0d, fnsum = 0d;
        for (FPIter x : truth) {
            if (x.isSet()) {
                fnsum += falseNegativeRate[k];
            } else {
                fpsum += falsePositiveRate[k];
            }
            ++k;
        }
        if (fpsum == 0) fpsum = 0.5d;
        if (fnsum == 0) fnsum = 0.5d;
        double scaleFp = expectedFalsePositives / fpsum;
        double scaleFn = expectedFalseNegatives / fnsum;

        if (false){
            System.out.printf("positives: %d, negatives: %d, expected FP: %.1f, expected FN: %.1f\nAverage #fp: %.1f, Average #fn: %.1f\n", positives, negatives,
                    expectedFalsePositives, expectedFalseNegatives, fpsum, fnsum);
        }

        final double[] vec = trainFps[0].toProbabilityArray();

        k=0;
        for (FPIter x : truth) {
            double random = r.nextDouble();
            if (x.isSet()) {
                if (random < (falseNegativeRate[k]*scaleFn)) {
                    vec[k] = draw(fns[k], r);
                } else {
                    vec[k] = draw(tps[k], r);
                }

            } else {
                if (random < (falsePositiveRate[k]*scaleFp)) {
                    vec[k] = draw(fps[k], r);
                } else {
                    vec[k] = draw(tns[k], r);
                }
            }
            ++k;
        }

        return vec;
    }

    public List<EvaluationInstance> readCrossvalidation(File file, HashMap<String,LabeledCompound> compounds) throws IOException {
        ArrayList<ErrorStats> errorStatList = new ArrayList<>();
        ArrayList<ProbabilityFingerprint> probabilityFingerprints = new ArrayList<>();
        ArrayList<ArrayFingerprint> perfectFingerprints = new ArrayList<>();
        ArrayList<EvaluationInstance> instances = new ArrayList<>();
        try (final BufferedReader br = FileUtils.getReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tbs = line.split("\t");
                if (exclude!=null && exclude.matcher(tbs[0]).find())
                    continue;
                if (include!=null && !include.matcher(tbs[0]).find())
                    continue;
                final String inchiKey = tbs[1].substring(0, 14);
                trainInchiKeys.add(inchiKey);
                ArrayFingerprint perfectPrediction = BooleanFingerprint.fromOneZeroString(version, tbs[3]);
                final double[] probs = new double[version.size()];
                for (int k = 4; k < tbs.length; ++k)
                    probs[k - 4] = Double.parseDouble(tbs[k]);
                final ProbabilityFingerprint trainFp = new ProbabilityFingerprint(version, probs);

                final Duplicate dup;
                if (structures.containsKey(inchiKey)) {
                    dup = structures.get(inchiKey);
                    if (dup.perfectFingerprint.tanimoto(perfectPrediction) < 1)
                        throw new RuntimeException("WTF?????? " + inchiKey + " with tanimoto " + dup.perfectFingerprint.tanimoto(perfectPrediction));
                } else {
                    dup = new Duplicate(inchiKey, perfectPrediction);
                    structures.put(inchiKey, dup);
                }
                perfectPrediction = dup.perfectFingerprint;
                dup.indizes.add(probabilityFingerprints.size());

                perfectFingerprints.add(perfectPrediction);
                probabilityFingerprints.add(trainFp);

                final LabeledCompound orig = compounds.get(inchiKey);
                if (orig!=null) {
                    instances.add(new EvaluationInstance(tbs[0], trainFp, orig));
                } else {
                    System.err.println("No classification information for " + tbs[0] + " with InChI-Key " + inchiKey);
                }

                int union = 0, intersection = 0, falsePositives = 0, falseNegatives = 0, npositives=0, nnegatives=0;

                // add platt pool
                int k = 0;
                for (FPIter2 fp : perfectPrediction.asBooleans().foreachPair(trainFp)) {
                    if (fp.isLeftSet()) {
                        ++npositives;
                        ++union;
                        positives[k].add(fp.getRightProbability());
                        if (fp.isRightSet()) {
                            tps[k].add(fp.getRightProbability());
                            ++intersection;
                        } else {
                            fns[k].add(fp.getRightProbability());
                            falseNegatives++;
                        }
                    } else {
                        ++nnegatives;
                        negatives[k].add(fp.getRightProbability());
                        if (fp.isRightSet()) {
                            fps[k].add(fp.getRightProbability());
                            ++union;
                            ++falsePositives;
                        } else {
                            tns[k].add(fp.getRightProbability());
                        }
                    }
                    ++k;
                }
                ErrorStats stat = new ErrorStats(
                        falsePositives / (double)nnegatives,
                        falseNegatives / (double)npositives,
                        intersection / (double)union
                );
                errorStatList.add(stat);
            }
        }
        this.errorStats = errorStatList.toArray(ErrorStats[]::new);
        Arrays.sort(errorStats, Comparator.comparingDouble(x->x.tanimoto));
        this.perfectFps = perfectFingerprints.toArray(new ArrayFingerprint[perfectFingerprints.size()]);
        this.trainFps = probabilityFingerprints.toArray(new ProbabilityFingerprint[probabilityFingerprints.size()]);

        // ADD SOME PSEUDOCOUNTS TO ARTIFICIALLY REDUCE RECALL FOR INSTANCES WITH FEW EXAMPLES
        {
            for (TDoubleArrayList x : fns) {
                x.add(5e-6);
                x.add(1e-5);
                x.add(1e-5);
                x.add(1e-4);
                x.add(1e-3);
            }
            for (TDoubleArrayList x : positives) {
                x.add(5e-6);
                x.add(1e-5);
                x.add(1e-5);
                x.add(1e-4);
                x.add(1e-3);
            }
        }

        for (TDoubleArrayList x : positives) x.sort();
        for (TDoubleArrayList x : negatives) x.sort();
        for (TDoubleArrayList x : tps) x.sort();
        for (TDoubleArrayList x : fps) x.sort();
        for (TDoubleArrayList x : tns) x.sort();
        for (TDoubleArrayList x : fns) x.sort();
        for (int i=0; i < recall.length; ++i) {
            recall[i] = tps[i].size() / ((double)(tps[i].size()+fns[i].size()));
            precision[i] = tps[i].size() / ((double)tps[i].size() + fps[i].size());
            truePositiveRate[i] = recall[i];
            falsePositiveRate[i] = fps[i].size() / ((double)(tns[i].size()+fps[i].size()));
            trueNegativeRate[i] = tns[i].size() / ((double)(tns[i].size()+fps[i].size()));
            falseNegativeRate[i] = fns[i].size() / ((double)fns[i].size() + tps[i].size());
        }


        return instances;
    }

    public ProbabilityFingerprint sampleIndependently(ArrayFingerprint truth, boolean noisy) {
        final Random random = new Random();
        boolean canopusFingerprintsAreReached = false;
        final double[] sampled = new double[version.size()];
        FPIter iter = truth.iterator();
        for (int k=0; k < sampled.length; ++k) {
            iter.next();
            if (iter.isSet()) {
                if (noisy && random.nextDouble()<0.05) {
                    sampled[k] = draw(fns[k],random);
                } else {
                    sampled[k] = draw(positives[k],random);
                }
            } else {
                if (noisy && random.nextDouble() < 0.002) {
                    sampled[k] = draw(fps[k],random);
                } else {
                    sampled[k] = draw(negatives[k],random);
                }
            }
        }
        return new ProbabilityFingerprint(version, sampled);
    }

    public ProbabilityFingerprint sampleFromCovariance(ArrayFingerprint truth) {
        return new ProbabilityFingerprint(version, covarianceTree.draw(truth));
    }

    public ProbabilityFingerprint sample(ArrayFingerprint truth, boolean noisy) {
        if (!TrainingData.SAMPLE_FROM_TEMPLATE_FINGERPRINTS) {
            System.err.println("ERROR! Sample from fingerprint templates should be disabled. omgomgomg wuppeldiwup!");
            System.exit(1);
        }
        final List<Scored<Duplicate>> alist = new ArrayList<>();
        final Random r = new Random();
        final double[] sampled = new double[version.size()];
        Arrays.fill(sampled, Double.NaN);
        ArrayFingerprint mod = singleSample(truth, sampled, r, alist, 0);
        int times=1;
        for (int k = 0; k < 5; ++k) {
            if (mod != null && mod.cardinality() >= 20) {
                mod = singleSample(mod, sampled, r, k<=1 ? alist : null, times);
                ++times;
            }
        }
        // sample missing
        int k = 0;
        int nans=0;
        int posChanged = 0, posChangedPos=0, posChangedNeg=0;
        boolean canopusFingerprintsAreReached = false;
        for (FPIter f : truth.iterator()) {
            if (Double.isNaN(sampled[k])) {
                ++nans;
                if (f.isSet()) {
                    sampled[k] = drawPositive(k, r);
                } else {
                    sampled[k] = drawNegative(k, r);
                }
            }

            if (noisy) {
                if (f.isSet() && sampled[k]>= 0.5) {
                    double freq = this.positives[k].size()/((double)this.negatives[k].size()+this.positives[k].size());
                    double simRecall = Math.min(1d, freq / 0.01);
                    final double recall = this.recall[k];
                    simRecall = (simRecall*recall*2)/(simRecall+recall);
                    if (r.nextDouble() > simRecall) {
                        // switch position
                        double before = sampled[k];
                        sampled[k] = draw(this.fns[k], r);
                       // System.err.printf(Locale.US, "%f for fp %d with recall %f and frequency %f -> switched from %f to %f\n",simRecall, f.getIndex(), recall, freq, before, sampled[k] );
                        ++posChanged;
                        ++posChangedPos;
                    }
                }
            }

            ++k;
        }


        if (noisy) {
            // add some fp from sample
            Collections.shuffle(alist);
            Scored<Sampler.Duplicate> chosenCandidate = alist.get(0);
            if (chosenCandidate!=null) {
                int j = 0;
                for (FPIter2 i : chosenCandidate.getCandidate().perfectFingerprint.foreachPair(truth)) {
                    if (i.isLeftSet() && !i.isRightSet()) {
                        if (r.nextDouble() > this.precision[j]) {
                            // swap
                            sampled[j] = draw(this.fps[j], r);
                            ++posChanged;
                            ++posChangedNeg;
                        }
                    }
                    ++j;
                }
            }
        }
        //System.out.println("Noisying changed " + posChanged +" positions. " + posChangedPos + " properties are removed, " + posChangedNeg + " properties are added. " + truth.cardinality() + " properties were part of the original molecule. Tanimoto to original is " + Tanimoto.probabilisticTanimoto(new ProbabilityFingerprint(version, sampled), truth).expectationValue() );
        //System.out.println(nans + " missing values after " + times + " iterations.");
        return new ProbabilityFingerprint(version, sampled);

    }

    public ArrayFingerprint singleSample(ArrayFingerprint truth, double[] sampled, Random r, List<Scored<Duplicate>> chooseable, int times) {
        // search similar fingerprints in training set
        final PriorityQueue<Scored<Duplicate>> bestMatching = new PriorityQueue<>(22);
        double threshold = Double.NEGATIVE_INFINITY;
        for (Duplicate dup : structures.values()) {
            final double tanimoto;
            if (times == 0) {
                tanimoto = dup.perfectFingerprint.tanimoto(truth);
            } else {
                tanimoto = truth.numberOfCommonBits(dup.perfectFingerprint)/Math.sqrt(dup.perfectFingerprint.cardinality()*truth.cardinality());
            }
            if (tanimoto < 0.95 && tanimoto > threshold) {
                bestMatching.add(new Scored<>(dup, tanimoto));
                if (bestMatching.size() > 100) {
                    bestMatching.poll();
                    threshold = bestMatching.peek().getScore();
                }
            }
        }
        final List<Scored<Duplicate>> chosenList = new ArrayList<>(bestMatching);
        Collections.shuffle(chosenList);
        Collections.sort(chosenList, Comparator.comparingInt(x->(int)(-x.getScore()*20)));
        if (chooseable!=null && !chosenList.isEmpty()) chooseable.addAll(chosenList.subList(0, Math.min(chosenList.size(),5)));
        if (chosenList.get(0).getScore() < 0.2) {
            //System.out.println(chosenList.get(0).getScore() + "is highest Tanimoto at round " + times);
            return null;
        }
        // pick randomly one fingerprint
        final Iterator<Scored<Duplicate>> iter = chosenList.iterator();
        Scored<Duplicate> chosenCandidate = null;
        while (iter.hasNext()) {
            chosenCandidate = iter.next();
            if (r.nextDouble() < 0.1) {
                break;
            }
        }
        //System.out.println(chosenList.get(0).getScore() + "is highest Tanimoto, "+ chosenCandidate.getScore() + " is chosen at round " + times);
        //System.out.println(times + ".) pick fingerprint with tanimoto " + chosenCandidate.getScore());
        final ArrayFingerprint chosenTruth = chosenCandidate.getCandidate().perfectFingerprint;
        // randomly pick a predicted fp
        final ProbabilityFingerprint chosenPredicted;
        {
            TIntArrayList inds = chosenCandidate.getCandidate().indizes;
            int picked = r.nextInt(inds.size());
            chosenPredicted = trainFps[inds.getQuick(picked)];
        }
        final boolean[] redo = truth.toBooleanArray();
        int k = 0;
        final FPIter pred = chosenPredicted.iterator();
        for (FPIter2 fp : truth.foreachPair(chosenTruth)) {
            pred.next();
            if (Double.isNaN(sampled[k]) && fp.isLeftSet() == fp.isRightSet()) {
                if (fp.isLeftSet()) redo[k] = false;
                if (fp.isLeftSet()) {
                    if (pred.getProbability() >= 0.5) {
                        sampled[k] = draw(tps[k], r);
                    } else {
                        sampled[k] = draw(fns[k], r);
                    }
                } else {
                    if (pred.getProbability() >= 0.5) {
                        sampled[k] = draw(fps[k], r);
                    } else {
                        sampled[k] = draw(tns[k], r);
                    }
                }
            }
            ++k;
        }
        return new BooleanFingerprint(version, redo).asArray();
    }

    protected double drawPositive(int k, Random r) {
        return draw(positives[k], r);
    }

    protected double drawNegative(int k, Random r) {
        return draw(negatives[k], r);
    }

    public static double draw(TDoubleArrayList distribution, Random r) {
        int k = r.nextInt(1 + distribution.size()) - 1;
        double q = r.nextDouble();
        double plattStart = (k < 0) ? 0 : distribution.get(k);
        double plattEnd = (k == distribution.size() - 1) ? 1d : distribution.get(k + 1);
        return plattStart + (plattEnd - plattStart) * q;
    }

    public void standardize(double[] plattNorm) {
        for (int i=0; i < plattNorm.length; ++i) {
            final TDoubleArrayList pos = positives[i];
            final TDoubleArrayList neg = negatives[i];
            double mean = (neg.sum() + pos.sum()) / (double)((pos.size()+neg.size()));
            plattNorm[i] = mean;
        }
    }
    public void standardize(double[] plattNorm, double[] scales) {
        for (int i=0; i < plattNorm.length; ++i) {
            final TDoubleArrayList pos = positives[i];
            final TDoubleArrayList neg = negatives[i];
            double mean = (neg.sum() + pos.sum()) / (double)((pos.size()+neg.size()));
            plattNorm[i] = mean;
        }
        for (int i=0; i < plattNorm.length; ++i) {
            final TDoubleArrayList pos = positives[i];
            final TDoubleArrayList neg = negatives[i];
            double d = 0d;
            for (int j=0; j < pos.size(); ++j) d += (pos.getQuick(j)*pos.getQuick(j));
            for (int j=0; j < neg.size(); ++j) d += (neg.getQuick(j)*neg.getQuick(j));
            d /= (pos.size()+neg.size());
            scales[i] = Math.sqrt(d);
        }
    }

    protected static class Sampled {
        private String inchikey;
        private ProbabilityFingerprint fingerprint;

        public Sampled(String inchikey, ProbabilityFingerprint fingerprint) {
            this.inchikey = inchikey;
            this.fingerprint = fingerprint;
        }
    }

}
