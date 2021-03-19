
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

package de.unijena.bioinf.ChemistryBase.ms.utils;

import de.unijena.bioinf.ChemistryBase.ms.MutableSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.Peak;
import de.unijena.bioinf.ChemistryBase.ms.SimplePeak;
import de.unijena.bioinf.ChemistryBase.ms.Spectrum;

import java.util.Arrays;

/**
 * Simple implementation of an immutable Mass Spectrum.
 * Peaks are stored ordered by mass in arrays.
 */
public class SimpleSpectrum extends BasicSpectrum<Peak> implements OrderedSpectrum<Peak>{

	private static SimpleSpectrum EMPTY = new SimpleSpectrum(new double[0], new double[0]);

	public static SimpleSpectrum empty() {
		return EMPTY;
	}

	protected int hash = 0;
	
	public SimpleSpectrum(double[] masses, double[] intensities) {
		this(new ArrayWrapperSpectrum(masses, intensities));
	}
	
	public <T extends Peak, S extends Spectrum<T>> SimpleSpectrum(S s) {
		super(orderedSpectrum(s));
	}

	@Override
	public double getMzAt(int index) {
		return masses[index];
	}
	
	@Override
	public double getIntensityAt(int index) {
		return intensities[index];
	}
	
	@Override
	public Peak getPeakAt(int index) {
		return new SimplePeak(masses[index], intensities[index]);
	}
	
	
	@Override
	public int hashCode() {
		if (hash!=0) return hash;
		this.hash = Arrays.hashCode(this.masses) ^ Arrays.hashCode(this.intensities);
		if (hash==0) hash=1;
		return hash;
	}

	@Override
	public SimpleSpectrum clone() {
		return new SimpleSpectrum(masses, intensities);
	}
		
	private static Spectrum<? extends Peak> orderedSpectrum(Spectrum<? extends Peak> s) {
        if (s instanceof OrderedSpectrum) return s;
        final MutableSpectrum<? extends Peak> t = new SimpleMutableSpectrum(s);
		Spectrums.sortSpectrumByMass(t);
		return t;
	}
	
	
}
