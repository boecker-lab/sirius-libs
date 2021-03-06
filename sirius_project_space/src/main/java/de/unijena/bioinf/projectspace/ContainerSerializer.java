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

package de.unijena.bioinf.projectspace;

import java.io.IOException;

public interface ContainerSerializer<S extends ProjectSpaceContainerId, T extends ProjectSpaceContainer<S>> {

    public void writeToProjectSpace(ProjectWriter writer, ProjectWriter.ForContainer<S,T> containerSerializer, S id, T container)  throws IOException;
    public T readFromProjectSpace(ProjectReader reader, ProjectReader.ForContainer<S,T> containerSerializer, S id)  throws IOException;
    public void deleteFromProjectSpace(ProjectWriter writer, ProjectWriter.DeleteContainer<S> containerSerializer, S id)  throws IOException;

}
