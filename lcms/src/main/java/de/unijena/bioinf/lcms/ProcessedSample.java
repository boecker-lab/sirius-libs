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

package de.unijena.bioinf.lcms;

import com.google.common.collect.Range;
import de.unijena.bioinf.ChemistryBase.math.RealDistribution;
import de.unijena.bioinf.ChemistryBase.ms.NoiseInformation;
import de.unijena.bioinf.lcms.noise.Ms2NoiseStatistics;
import de.unijena.bioinf.lcms.noise.NoiseModel;
import de.unijena.bioinf.lcms.quality.Quality;
import de.unijena.bioinf.lcms.quality.QualityAnnotation;
import de.unijena.bioinf.model.lcms.ChromatogramCache;
import de.unijena.bioinf.model.lcms.FragmentedIon;
import de.unijena.bioinf.model.lcms.LCMSRun;
import de.unijena.bioinf.model.lcms.Scan;
import de.unijena.bioinf.ms.annotations.Annotated;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Identity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ProcessedSample implements Annotated<DataAnnotation> {

    public final LCMSRun run;
    public final NoiseModel ms1NoiseModel, ms2NoiseModel;

    // ms2 noise model
    public final NoiseInformation ms2NoiseInformation;

    public final ChromatogramCache chromatogramCache;
    public final SpectrumStorage storage;
    public final ChromatogramBuilder builder;
    protected double meanPeakWidthToHeightRatioStd;

    protected double meanPeakWidth, meanPeakWidthToHeightRatio;

    protected final Annotations<DataAnnotation> annotations;
    protected UnivariateFunction recalibrationFunction;
    protected double maxRT;
    public final ArrayList<FragmentedIon> ions;
    public final ArrayList<FragmentedIon> gapFilledIons;
    public final ArrayList<FragmentedIon> otherIons; // any ions used for alignment solely

    // can be used for multiple charge detection
    protected RealDistribution intensityAfterPrecursorDistribution;

    ProcessedSample(LCMSRun run, NoiseModel ms1NoiseModel, Ms2NoiseStatistics ms2NoiseModel, ChromatogramCache chromatogramCache, SpectrumStorage storage) {
        this.run = run;
        this.ms1NoiseModel = ms1NoiseModel;
        this.ms2NoiseInformation = ms2NoiseModel.done();
        this.ms2NoiseModel = ms2NoiseModel.getGlobalNoiseModel();
        this.chromatogramCache = chromatogramCache;
        this.storage = storage;
        this.builder = new ChromatogramBuilder(this);
        this.ions = new ArrayList<>();
        this.maxRT = run.getScans().stream().max(Comparator.comparingLong(Scan::getRetentionTime)).map(x->x.getRetentionTime()).orElse(1l);
        this.recalibrationFunction = new Identity();
        this.annotations = new Annotations<>();
        this.gapFilledIons = new ArrayList<>();
        this.otherIons = new ArrayList<>();
    }

    public RealDistribution getIntensityAfterPrecursorDistribution() {
        return intensityAfterPrecursorDistribution;
    }

    public void setIntensityAfterPrecursorDistribution(RealDistribution intensityAfterPrecursorDistribution) {
        this.intensityAfterPrecursorDistribution = intensityAfterPrecursorDistribution;
    }

    public NavigableMap<Integer,Scan> findScansByRT(Range<Long> rt) {
        // stupid java =/
        Scan a=null, b=null;
        for (Scan s : run.getScans()) {
            if (a==null && rt.contains(s.getRetentionTime())) {
                a = s;
            }
            if (a!=null && !rt.contains(s.getRetentionTime())) {
                b = s;
            }
        }
        if (b == null) b = run.getScanByNumber(run.scanRange().upperEndpoint()).get();
        return run.getScans(a.getIndex(), b.getIndex());
    }
    public NavigableMap<Integer,Scan> findScansByRecalibratedRT(Range<Double> rt) {
        // stupid java =/
        Scan a=null, b=null;
        for (Scan s : run.getScans()) {
            if (a==null && rt.contains(getRecalibratedRT(s.getRetentionTime()))) {
                a = s;
            }
            if (a!=null && !rt.contains(getRecalibratedRT(s.getRetentionTime()))) {
                b = s;
                break;
            }
        }
        if (a==null) return new TreeMap<>();
        if (b == null) b = run.getScanByNumber(run.scanRange().upperEndpoint()).get();
        return run.getScans(a.getIndex(), b.getIndex());
    }
/*
    public NavigableMap<Integer,Scan> findScansByNormalizedRecalibratedRT(Range<Double> rt) {
        // stupid java =/
        Scan a=null, b=null;
        for (Scan s : run.getScans()) {
            if (a==null && rt.contains(getNormalizedRecalibratedRT(s.getRetentionTime()))) {
                a = s;
            }
            if (a!=null && !rt.contains(getNormalizedRecalibratedRT(s.getRetentionTime()))) {
                b = s;
                break;
            }
        }
        if (b == null) b = run.getScanByNumber(run.scanRange().upperEndpoint()).get();
        if (a==null)
            return run.getScansAfter(run.scanRange().upperEndpoint());
        return run.getScans(a.getScanNumber(), b.getScanNumber());
    }

    public double getNormalizedRecalibratedRT(long retentionTime) {
        return recalibrationFunction.value(retentionTime / maxRT);
    }

    public double getNormalizedRT(long retentionTime) {
        return retentionTime / maxRT;
    }
    */


    public double getRecalibratedRT(long retentionTime) {
        return recalibrationFunction.value(retentionTime);
    }

    public UnivariateFunction getRecalibrationFunction() {
        return recalibrationFunction;
    }

    public void setRecalibrationFunction(UnivariateFunction recalibrationFunction) {
        this.recalibrationFunction = recalibrationFunction;
    }

    public Quality getBestQualityTerm() {
        Quality best = Quality.UNUSABLE;
        for (Class<DataAnnotation> a : annotations) {
            if (a.isAssignableFrom(QualityAnnotation.class)) {
                QualityAnnotation ano = (QualityAnnotation) getAnnotationOrThrow(a);
                if (ano.getQuality().betterThan(best))
                    best = ano.getQuality();
            }
        }
        return best;
    }
    public Quality getLowestQualityTerm() {
        Quality best = Quality.GOOD;
        for (Class<DataAnnotation> a : annotations) {
            if (a.isAssignableFrom(QualityAnnotation.class)) {
                QualityAnnotation ano = (QualityAnnotation) getAnnotationOrThrow(a);
                if (best.betterThan(ano.getQuality()))
                    best = ano.getQuality();
            }
        }
        return best;
    }

    public String toString() {
        return run.getIdentifier();
    }

    public double getMeanPeakWidth() {
        return meanPeakWidth;
    }

    public double getMeanPeakWidthToHeightRatio() {
        return meanPeakWidthToHeightRatio;
    }
    public double getMeanPeakWidthToHeightStd() {
        return meanPeakWidthToHeightRatio;
    }

    @Override
    public Annotations<DataAnnotation> annotations() {
        return annotations;
    }

}
