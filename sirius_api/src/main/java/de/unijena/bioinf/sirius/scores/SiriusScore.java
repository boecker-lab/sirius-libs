package de.unijena.bioinf.sirius.scores;

public class SiriusScore extends FormulaScore {

    public SiriusScore(double score) {
        super(score);
    }

    @Override
    public String name() {
        return "siriusScore";
    }

    @Override
    public ScoreType getScoreType() {
        return ScoreType.Logarithmic;
    }
}