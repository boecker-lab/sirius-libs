package de.unijena.bioinf.ChemistryBase.ms.ft.model;

import de.unijena.bioinf.ms.annotations.Ms2ExperimentAnnotation;
import de.unijena.bioinf.ms.properties.DefaultProperty;

public class IsotopeSettings implements Ms2ExperimentAnnotation {

    /**
     * When filtering enabled, molecular formulas are excluded if their theoretical isotope pattern does not match
     * the theoretical one, even if their MS/MS pattern has high score.
     */
    @DefaultProperty protected boolean filter;

    /**
     * multiplier for the isotope score. Set to 0 to disable isotope scoring.
     */
    @DefaultProperty protected double multiplier;

    public IsotopeSettings() {
        this.filter = true;
        this.multiplier = 1d;
    }

    public IsotopeSettings(boolean filter, double multiplicator) {
        if (multiplicator < 0 || !Double.isFinite(multiplicator))
            throw new IllegalArgumentException(String.valueOf(multiplicator) + " is invalid. Multiplicator has to be a positive (or zero) numerical value");
        this.filter = filter;
        this.multiplier = multiplicator;
    }

    public boolean isFiltering() {
        return filter;
    }

    public boolean isScoring() {
        return multiplier > 0d;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
