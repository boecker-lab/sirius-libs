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

package de.unijena.bioinf.GibbsSampling.model;

import de.unijena.bioinf.ChemistryBase.chem.Ionization;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;

/**
 * Created by ge28quv on 16/05/17.
 */
public class FragmentWithIndex implements Comparable<FragmentWithIndex> {
    public final MolecularFormula mf;
    public final short idx;
    public final double score;
    private final Ionization ionization;

    public FragmentWithIndex(MolecularFormula mf, Ionization ion, short idx, double score) {
        this.mf = mf;
        this.idx = idx;
        this.score = score;
        this.ionization = ion;
    }

    @Override
    public int compareTo(FragmentWithIndex o) {
        return mf.compareTo(o.mf);
    }

    public MolecularFormula getFormula() {
        return mf;
    }

    public short getIndex() {
        return idx;
    }

    public double getScore() {
        return score;
    }

    public Ionization getIonization() {
        return ionization;
    }

    @Override
    public String toString() {
        return "{" + mf + ", " + ionization + ", idx: " + idx +", score: " + score + "}";
    }
}
