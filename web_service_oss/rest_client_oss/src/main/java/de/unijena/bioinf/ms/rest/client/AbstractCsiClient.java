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

package de.unijena.bioinf.ms.rest.client;

import de.unijena.bioinf.ChemistryBase.utils.IOFunctions;
import de.unijena.bioinf.fingerid.utils.FingerIDProperties;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractCsiClient extends AbstractClient {
    protected static final String API_ROOT = "/api";

    protected AbstractCsiClient(@Nullable URI serverUrl, IOFunctions.@NotNull IOConsumer<HttpUriRequest>... requestDecorators) {
        super(serverUrl, requestDecorators);
    }

    protected AbstractCsiClient(@NotNull Supplier<URI> serverUrl, IOFunctions.@NotNull IOConsumer<HttpUriRequest>... requestDecorators) {
        super(serverUrl, requestDecorators);
    }

    protected AbstractCsiClient(@Nullable URI serverUrl, @NotNull List<IOFunctions.IOConsumer<HttpUriRequest>> requestDecorators) {
        super(serverUrl, requestDecorators);
    }

    protected AbstractCsiClient(@NotNull Supplier<URI> serverUrl, @NotNull List<IOFunctions.IOConsumer<HttpUriRequest>> requestDecorators) {
        super(serverUrl, requestDecorators);
    }

    //region PathBuilderMethods

    // WebAPI paths //todo should be moved to a separate class on level up to make tis class API independent
    protected StringBuilder getWebAPIBasePath() {
        return new StringBuilder(API_ROOT);
    }

    protected URIBuilder buildWebapiURI(@Nullable final String path) {
        StringBuilder pathBuilder = getWebAPIBasePath();

        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/"))
                pathBuilder.append("/");

            pathBuilder.append(path);
        }

        return getBaseURI(pathBuilder.toString());
    }

    protected URIBuilder buildVersionSpecificWebapiURI(@Nullable String path) {
        return buildWebapiURI(path);
    }

    @Override
    public URIBuilder getBaseURI(@Nullable String path) {
//        if (getServerUrl() == null)
//            throw new NullPointerException("Service URL is null. This might be caused by a missing login.");

        return super.getBaseURI(path);
    }

    protected String makeVersionContext() {
        return "/v" + FingerIDProperties.fingeridMinorVersion();
    }
    //endregion

}
