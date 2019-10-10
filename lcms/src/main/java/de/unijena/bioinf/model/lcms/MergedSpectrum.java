package de.unijena.bioinf.model.lcms;

import com.google.common.collect.Range;
import de.unijena.bioinf.ChemistryBase.ms.Peak;
import de.unijena.bioinf.ChemistryBase.ms.Spectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.*;
import de.unijena.bioinf.lcms.quality.Quality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MergedSpectrum extends PeaklistSpectrum<MergedPeak> implements OrderedSpectrum<MergedPeak> {

    protected Precursor precursor;
    protected List<Scan> scans;
    protected double noiseLevel;

    public MergedSpectrum(Scan scan, Spectrum<? extends Peak> spectrum, Precursor precursor) {
        super(new ArrayList<>());
        for (int k=0; k < spectrum.size(); ++k) {
            peaks.add(new MergedPeak(new ScanPoint(scan, spectrum.getMzAt(k), spectrum.getIntensityAt(k))));
        }
        peaks.sort(Comparator.comparingDouble(Peak::getMass));
        this.precursor= precursor;
        scans = new ArrayList<>();
        scans.add(scan);
    }

    public MergedSpectrum(Precursor precursor, List<MergedPeak> peaks, List<Scan> scans) {
        super(peaks);
        this.peaks.sort(Comparator.comparingDouble(Peak::getMass));
        this.scans = scans;
        this.precursor=precursor;
    }

    // we have to do this. Otherwise, memory consumption is just too high
    public void applyNoiseFiltering() {
        int min = (int)Math.floor(scans.size()*0.2);
        this.peaks.removeIf(x->x.getIntensity()<noiseLevel || x.getSourcePeaks().length < min);
    }

    public double getNoiseLevel() {
        return noiseLevel;
    }

    public void setNoiseLevel(double noiseLevel) {
        this.noiseLevel = noiseLevel;
    }

    public List<Scan> getScans() {
        return scans;
    }

    public double totalTic() {
        return Spectrums.calculateTIC(this, Range.closed(0d,precursor.getMass()-20), noiseLevel);
    }

    public Precursor getPrecursor() {
        return precursor;
    }

    public SimpleSpectrum finishMerging() {
        final int n = scans.size();
        int mostIntensive = scans.stream().max(Comparator.comparingDouble(Scan::getTIC)).map(x->x.getIndex()).orElse(-1);
        if (n >= 5) {
            int min = (int)Math.ceil(n*0.2);
            final SimpleMutableSpectrum buf = new SimpleMutableSpectrum();
            for (MergedPeak p : peaks) {
                if (p.getMass() > (this.precursor.getMass()+10))
                    continue;
                if (p.getSourcePeaks().length >= min) {
                    buf.addPeak(p);
                } else if (p.getIntensity() > 2*noiseLevel) {
                    for (ScanPoint q : p.getSourcePeaks()) {
                        if (q.getScanNumber()==mostIntensive) {
                            buf.addPeak(p);
                            break;
                        }
                    }
                }
            }
            return new SimpleSpectrum(buf);
        } else {
            final SimpleMutableSpectrum buf = new SimpleMutableSpectrum(this);
            Spectrums.applyBaseline(buf, 2*noiseLevel);
            Spectrums.cutByMassThreshold(buf,precursor.getMass()-20);
            return new SimpleSpectrum(buf);
        }
    }

    public Quality getQuality(SimpleSpectrum mergedSpectrum) {
        int peaksAboveNoise = 0;
        for (int k=0; k < mergedSpectrum.size(); ++k) {
            if (mergedSpectrum.getMzAt(k) < (getPrecursor().getMass()-20) && mergedSpectrum.getIntensityAt(k) >= noiseLevel*3)
                ++peaksAboveNoise;
        }
        if (peaksAboveNoise >= 5) return Quality.GOOD;
        if (peaksAboveNoise >= 3) return Quality.DECENT;
        return Quality.BAD;
    }
}
