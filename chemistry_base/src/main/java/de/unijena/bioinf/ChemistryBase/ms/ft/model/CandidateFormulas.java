/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer and Sebastian Böcker,
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
 *  You should have received a copy of the GNU Lesser General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.ChemistryBase.ms.ft.model;

import de.unijena.bioinf.ms.annotations.Ms2ExperimentAnnotation;
import de.unijena.bioinf.ms.properties.DefaultInstanceProvider;
import de.unijena.bioinf.ms.properties.DefaultProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This configuration holds a set of user given formulas to be used as candidates for SIRIUS
 * Note: This set might be merged with other sources like formulas from databases
 */
public class CandidateFormulas implements Ms2ExperimentAnnotation {

    @NotNull
    protected final Whiteset formulas;

    public CandidateFormulas(@NotNull Whiteset formulas) {
        this.formulas = formulas;
    }

    /**
     * @param value Set of Molecular Formulas to be used as candidates for molecular formula estimation with SIRIUS
     */
    @DefaultInstanceProvider
    public static CandidateFormulas newInstance(@DefaultProperty List<String> value) {
        return new CandidateFormulas(Whiteset.of(value));
    }

    public Whiteset formulas() {
        return formulas;
    }
}
