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

package de.unijena.bioinf.projectspace.sirius;

import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.lcms.LCMSPeakInformation;
import de.unijena.bioinf.projectspace.ProjectSpaceConfiguration;

@Deprecated
public class SiriusProjectSpaceConfiguration {

    public static void configure(ProjectSpaceConfiguration configuration) {

        configuration.registerContainer(CompoundContainer.class, new CompoundContainerSerializer());
        configuration.registerContainer(FormulaResult.class, new FormulaResultSerializer());

        configuration.registerComponent(CompoundContainer.class, Ms2Experiment.class, new MsExperimentSerializer());

        configuration.registerComponent(FormulaResult.class, FTree.class, new TreeSerializer());


        configuration.registerComponent(CompoundContainer.class, LCMSPeakInformation.class, new  LCMSPeakSerializer());

    }

}
