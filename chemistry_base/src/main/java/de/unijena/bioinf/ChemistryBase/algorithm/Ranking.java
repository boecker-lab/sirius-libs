package de.unijena.bioinf.ChemistryBase.algorithm;

import com.google.common.base.Equivalence;
import com.google.common.collect.Range;
import gnu.trove.list.array.TIntArrayList;

import java.util.List;

/**
 *
 */
public class Ranking {

    private int[] fromRank, toRank, maxRank;
    private double[] rankDistribution, randomDistribution;

    public static Builder build(int maxRanking) {
        return new Builder(maxRanking);
    }

    private Ranking(int maxRanking, int[] fromRank, int[] toRank, int[] maxRank) {
        this.fromRank = fromRank;
        this.toRank = toRank;
        this.maxRank = maxRank;
        this.rankDistribution = new double[maxRanking];
        this.randomDistribution = new double[maxRanking];
        double restBin = 0d, restBinRandom=0d;
        for (int k=0; k < fromRank.length; ++k) {
            final int from = fromRank[k];
            final int to = toRank[k];
            final double x = 1d/(1+to-from);
            final double random = 1d/maxRank[k];
            for (int i=from; i < Math.min(to, rankDistribution.length); ++i) {
                rankDistribution[i] += x;
            }
            for (int i=0; i < Math.min(maxRank[k], maxRanking); ++i) randomDistribution[i]+=random;
            restBin += Math.max(0, to - Math.min(to, rankDistribution.length))*x;
            restBinRandom += Math.max(0, maxRank[k] - Math.min(maxRank[k], randomDistribution.length))*random;
        }
        double sum = restBin;
        for (double val : rankDistribution)  sum += val;
        double cumsum = 0d;
        for (int k=0; k < rankDistribution.length; ++k) {
            cumsum += rankDistribution[k];
            rankDistribution[k] = cumsum / sum;
        }
        sum = restBinRandom;
        for (double val : randomDistribution)  sum += val;
        cumsum = 0d;
        for (int k=0; k < randomDistribution.length; ++k) {
            cumsum += randomDistribution[k];
            randomDistribution[k] = cumsum / sum;
        }
    }

    public Range<Integer> getRanking(int index) {
        return Range.closed(fromRank[index], toRank[index]);
    }

    public int getMinRank(int index) {
        return fromRank[index];
    }

    public int getMaxRank(int index) {
        return toRank[index];
    }

    public double getAverageRank(int index) {
        return fromRank[index] + (toRank[index]-fromRank[index])/2;
    }

    public double withinTop(int k) {
        return rankDistribution[k];
    }

    public double withinTopByRandom(int k) {
        return randomDistribution[k];
    }

    public static class Builder {

        private TIntArrayList from,to,max;
        private int mx;

        private Builder(int mx) {
            this.from = new TIntArrayList();
            this.to = new TIntArrayList();
            this.max = new TIntArrayList();
            this.mx = mx;
        }

        public Builder update(int from, int to, int max) {
            this.from.add(from);
            this.to.add(to);
            this.max.add(max);
            return this;
        }

        public <T> Builder update(List<Scored<T>> orderedList, T candidate) {
            return update(orderedList, candidate, (Equivalence<T>) Equivalence.equals());
        }

        public <T> Builder update(List<Scored<T>> orderedList, T candidate, Equivalence<T> equiv) {
            if (orderedList.isEmpty()) return this;
            for (int k=0; k < orderedList.size(); ++k) {
                if (equiv.equivalent(candidate, orderedList.get(k).getCandidate())) {
                    // search for other candidates which are equivalent
                    final double optScore = orderedList.get(k).getScore();
                    int before=k-1, next=k+1;
                    while (before >= 0 && (Math.abs(orderedList.get(before).getScore()-optScore)<1e-12)) --before;
                    ++before;
                    while (next < orderedList.size() && (Math.abs(orderedList.get(next).getScore()-optScore)<1e-12)) ++next;
                    --next;
                    update(before, next, orderedList.size());
                    return this;
                }
            }
            update(0, orderedList.size(), orderedList.size());
            return this;
        }

        public Ranking done() {
            return new Ranking(mx, from.toArray(), to.toArray(), max.toArray());
        }
    }

}