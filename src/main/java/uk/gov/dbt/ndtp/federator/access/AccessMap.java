// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessAPI;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessDetails;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;

/***
 * Represents a mapping of the client IDs and their access details/topics
 */
public class AccessMap {

    public static final String HASHING_ALGORITHM = "SHA3-256";
    private static final AccessMap singleton = new AccessMap();
    private final Map<String, AccessDetails> underlyingMap;

    private static final Logger LOGGER = LoggerFactory.getLogger("AccessMap");

    private AccessMap() {
        underlyingMap = new ConcurrentHashMap<>();
    }

    public static AccessMap get() {
        return singleton;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static void initFromFile(File f) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, AccessDetails>> typeRef = new TypeReference<Map<String, AccessDetails>>() {};

        Map<String, AccessDetails> fromFile = mapper.readValue(f, typeRef);
        singleton.underlyingMap.clear();
        singleton.underlyingMap.putAll(fromFile);
    }

    public void add(String key, AccessDetails value) {
        underlyingMap.put(key, value);
    }

    public AccessDetails getDetails(String key) {
        return underlyingMap.get(key);
    }

    public void remove(String key) {
        underlyingMap.remove(key);
    }

    public List<String> getClientNames() {
        return List.copyOf(underlyingMap.keySet());
    }

    public void update(String key, AccessDetails value) {
        underlyingMap.put(key, value);
    }

    public void verifyDetails(String key, String password) throws AccessDeniedException {
        final MessageDigest digest;

        AccessDetails details = getDetails(key);
        if (null == details) {
            throw new AccessDeniedException("No access details stored for " + key);
        }
        AccessAPI api = details.getApi();
        if (null == api) {
            throw new AccessDeniedException("No credentials stored for " + key);
        }
        if (api.isRevoked()) {
            throw new AccessDeniedException("Credentials revoked for " + key);
        }
        // TO-DO: Add actual password hashing and comparison (needs synced with Access
        // component method)

        try {
            // note the below digest is not thread safe, therefore we are getting a new instance
            // this is not computationally expensive. Maybe Apache Commons DigestUtils
            digest = MessageDigest.getInstance(HASHING_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            String errorMessage = String.format("The Hashing Algorithm String is Wrong '%s'", HASHING_ALGORITHM);
            throw new RuntimeException(errorMessage, e);
        }

        String passwordWithSalt = password + api.getSalt();

        byte[] hashBytes = digest.digest(passwordWithSalt.getBytes(StandardCharsets.UTF_8));
        String calculatedHash = bytesToHex(hashBytes);

        if (!calculatedHash.equals(api.getHashed_key())) {
            String badHashErrorMessage = String.format(
                    "Bad Hash. Caller '%s'.%nHash Calculated '%s'%nStored Hash '%s'.%nSalt '%s'.%nHash with '%S'",
                    key, calculatedHash, api.getHashed_key(), api.getSalt(), HASHING_ALGORITHM);
            throw new AccessDeniedException(badHashErrorMessage);
        } else {
            LOGGER.info("Stored and calculated hash values match for {}", key);
        }
    }

    /**
     * The following methods are mainly for testing purposes
     */
    public int size() {
        return underlyingMap.size();
    }

    public boolean isEmpty() {
        return underlyingMap.isEmpty();
    }

    public void clear() {
        underlyingMap.clear();
    }
}
