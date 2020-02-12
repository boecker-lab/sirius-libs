package de.unijena.bioinf.model.lcms;

/**
 * A spectrum which can be tracked back to a Scan within an LCMS source file
 */
public class Scan {

    /**
     * Unique index usually the scanNumber or scanNumber - 1
     */
    private final int index;

    /**
     * retention time in milliseconds
     */
    private final long retentionTime;

    private final Polarity polarity;

    /**
     * For MS/MS only: precursor information
     */
    private final Precursor precursor;

    private final double TIC;
    private final int numberOfPeaks;
    private final double collisionEnergy;
    private final boolean centroided;

    public Scan(int index, Polarity polarity, long retentionTime, double collisionEnergy, int numberOfPeaks, double TIC, boolean centroided) {
        this(index,polarity,retentionTime,collisionEnergy,numberOfPeaks,TIC,centroided,null);
    }

    public Scan(int index, Polarity polarity, long retentionTime, double collisionEnergy, int numberOfPeaks, double TIC, boolean centroided, Precursor precursor) {
        this.index = index;
        this.retentionTime = retentionTime;
        this.collisionEnergy=collisionEnergy;
        this.precursor = precursor;
        this.polarity = polarity;
        this.TIC = TIC;
        this.numberOfPeaks = numberOfPeaks;
        this.centroided = centroided;
    }

    public boolean isCentroided() {
        return centroided;
    }

    public boolean isProfiled() {
        return !centroided;
    }

    public int getNumberOfPeaks() {
        return numberOfPeaks;
    }

    public double getTIC() {
        return TIC;
    }

    public int getIndex() {
        return index;
    }

    public boolean isMsMs() {
        return precursor!=null;
    }

    public Precursor getPrecursor() {
        return precursor;
    }

    public Polarity getPolarity() {
        return polarity;
    }

    public long getRetentionTime() {
        return retentionTime;
    }

    public double getCollisionEnergy(){return collisionEnergy;}

    @Override
    public String toString() {
        return precursor!=null ? ("MS/MS " + index + ", m/z = " + precursor.getMass()) : "MS " + index;
    }
}