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

package de.unijena.bioinf.storage.blob.minio;

import de.unijena.bioinf.ms.properties.PropertyManager;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class MinIoUtils {

    public static final String URL_PREFIX = "mio://";

    public static MinIoBlobStorage createDefaultMinIoStorage(@NotNull String bucketName) {
        String accessKey = PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.accessKey");
        String secretKey = PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.secretKey");
        return createDefaultMinIoStorage(bucketName, accessKey, secretKey);
    }

    public static MinIoBlobStorage createDefaultMinIoStorage(@NotNull String bucketName, @NotNull String accessKey, @NotNull String secretKey) {
        String domain = PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.host");
        return createDefaultMinIoStorage(bucketName, domain, accessKey, secretKey);
    }

    public static MinIoBlobStorage createDefaultMinIoStorage(@NotNull String bucketName, @NotNull String domain, @NotNull String accessKey, @NotNull String secretKey) {
        return new MinIoBlobStorage(bucketName, createClient(domain, accessKey, secretKey));
    }

    public static MinioClient createClient(@NotNull String url, @NotNull String accessKey, @NotNull String secretKey) {
        MinioClient client = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
//        client.traceOn(System.out);
        return client;
    }

    public static boolean bucketExists(@NotNull String bucketName) {
        return bucketExists(bucketName, createClient(
                PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.host"),
                PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.accessKey"),
                PropertyManager.getProperty("de.unijena.bioinf.ms.stores.model.s3.secretKey"))
        );
    }
    public static boolean bucketExists(@NotNull String bucketName, @NotNull MinioClient minioClient) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (ErrorResponseException | InvalidResponseException | IOException | InsufficientDataException | InternalException | InvalidKeyException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new RuntimeException("Error when searching for bucket: " + bucketName, e);
        }
    }
}
