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

package de.unijena.bioinf.ms.rest.client.canopus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unijena.bioinf.ChemistryBase.utils.IOFunctions;
import de.unijena.bioinf.fingerid.predictor_types.PredictorType;
import de.unijena.bioinf.ms.rest.client.AbstractClient;
import de.unijena.bioinf.ms.rest.model.JobUpdate;
import de.unijena.bioinf.ms.rest.model.canopus.CanopusCfData;
import de.unijena.bioinf.ms.rest.model.canopus.CanopusJobInput;
import de.unijena.bioinf.ms.rest.model.canopus.CanopusJobOutput;
import de.unijena.bioinf.ms.rest.model.canopus.CanopusNpcData;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class CanopusClient extends AbstractClient {

    public CanopusClient(@Nullable URI serverUrl, @NotNull IOFunctions.IOConsumer<HttpUriRequest> requestDecorator) {
        super(serverUrl, requestDecorator);
    }

    public CanopusCfData getCfData(PredictorType predictorType, CloseableHttpClient client) throws IOException {
        return executeDataRequest(predictorType, client, "/canopus/cf-data", CanopusCfData::read);
    }

    public CanopusNpcData getNpcData(PredictorType predictorType, CloseableHttpClient client) throws IOException {
        return executeDataRequest(predictorType, client, "/canopus/npc-data", CanopusNpcData::read);
    }

    private <T> T executeDataRequest(PredictorType predictorType, CloseableHttpClient client, String path, IOFunctions.IOFunction<BufferedReader, T> respHandling) throws IOException {
        return execute(client,
                () -> new HttpGet(buildVersionSpecificWebapiURI(path)
                        .setParameter("predictor", predictorType.toBitsAsString())
                        .build()),
                respHandling
        );
    }

    public JobUpdate<CanopusJobOutput> postJobs(final CanopusJobInput input, CloseableHttpClient client) throws IOException {
        return executeFromJson(client,
                () -> {
                    final HttpPost post = new HttpPost(buildVersionSpecificWebapiURI("/canopus/" + CID + "/jobs").build());
                    String v = new ObjectMapper().writeValueAsString(input);
                    post.setEntity(new StringEntity(v, StandardCharsets.UTF_8));
                    post.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
                    return post;
                }, new TypeReference<>() {
                }
        );
    }
}

