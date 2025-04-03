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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessAPI;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessDetails;
import uk.gov.dbt.ndtp.federator.access.mappings.AccessTopics;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;

class AccessMapTest {
    @Test
    void test_verifyDetails_emptyMap() {
        // given
        AccessMap map = AccessMap.get();
        map.clear();
        // when
        // then
        assertThrowsExactly(AccessDeniedException.class, () -> map.verifyDetails("Key", "hashed_key"));
    }

    @Test
    void test_verifyDetails_missingEntry() {
        // given
        AccessMap map = AccessMap.get();
        map.add("Key1", new AccessDetails());
        // when
        // then
        assertThrowsExactly(AccessDeniedException.class, () -> map.verifyDetails("Key", "hashed_key"));
    }

    @Test
    void test_verifyDetails_noCredentials() {
        // given
        AccessMap map = AccessMap.get();
        map.add("Key1", new AccessDetails());
        // when
        // then
        assertThrowsExactly(AccessDeniedException.class, () -> map.verifyDetails("Key1", "hashed_key"));
    }

    @Test
    void test_verifyDetails_revoked() {
        // given
        AccessMap map = AccessMap.get();
        AccessDetails accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Existing_Client");
        accessDetails.setTopics(
                new ArrayList<>(List.of(new AccessTopics("Topic-1", null), new AccessTopics("Topic-2", null))));
        AccessAPI accessAPI = new AccessAPI();
        accessAPI.setHashed_key("hashed_key");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(true);
        accessDetails.setApi(accessAPI);
        map.add("Key1", accessDetails);

        // when
        // then
        assertThrowsExactly(AccessDeniedException.class, () -> map.verifyDetails("Key1", "hashed_key"));
    }

    @Test
    void test_verifyDetails_incorrectPassword() {
        // given
        AccessMap map = AccessMap.get();
        AccessDetails accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Existing_Client");
        accessDetails.setTopics(
                new ArrayList<>(List.of(new AccessTopics("Topic-1", null), new AccessTopics("Topic-2", null))));
        AccessAPI accessAPI = new AccessAPI();
        accessAPI.setHashed_key("hashed_key");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        map.add("Key1", accessDetails);

        // when
        // then
        assertThrowsExactly(AccessDeniedException.class, () -> map.verifyDetails("Key1", "wrong_hashed_key"));
    }

    @Test
    void test_verifyDetails_happyPath() {
        // given
        AccessMap map = AccessMap.get();
        AccessDetails accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Existing_Client");
        accessDetails.setTopics(
                new ArrayList<>(List.of(new AccessTopics("Topic-1", null), new AccessTopics("Topic-2", null))));
        AccessAPI accessAPI = new AccessAPI();
        accessAPI.setHashed_key("cbca7aa52f4d97ef7c005021cd9438039e58ac1b54a0aa20730ced30b10f1249");
        accessAPI.setSalt("23dfgTrfhJK879Luifxzcv");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        map.add("Key1", accessDetails);
        // when
        // then
        assertDoesNotThrow(() -> map.verifyDetails("Key1", "hashed_key"));
    }

    @Test
    void test_concurrency_multiple_clients_with_different_passwords() {
        var clientMap = AccessMap.get();
        var accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Client1");
        accessDetails.setTopics(List.of(new AccessTopics("Topic-1", null)));
        var accessAPI = new AccessAPI();
        accessAPI.setHashed_key("f9e32f751ea86df7e9b281ca618def7a8551970660e3e0a361dd93b4d6ec6ed1");
        accessAPI.setSalt("This-is-a-test-salt");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        clientMap.add("Client1", accessDetails);

        accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Client2");
        accessDetails.setTopics(List.of(new AccessTopics("Topic-2", null)));
        accessAPI = new AccessAPI();
        accessAPI.setHashed_key("f89e6652be40b81120cdd2507bed9a3aeed41f3f22cbdd329494997eb5fb523b");
        accessAPI.setSalt("This-is-a-test-salt");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        clientMap.add("Client2", accessDetails);

        var executor = Executors.newCachedThreadPool();
        var lock = new CountDownLatch(4);
        try {
            /* Call multiple times in order to test concurrency */
            var task1 = executor.submit(createTask("Client1", "This-is-the-client1-test-password", lock));
            var task2 = executor.submit(createTask("Client2", "This-is-the-client2-test-password", lock));
            var task3 = executor.submit(createTask("Client1", "This-is-the-client1-test-password", lock));
            var task4 = executor.submit(createTask("Client2", "This-is-the-client2-test-password", lock));

            assertDone(task1);
            assertDone(task2);
            assertDone(task3);
            assertDone(task4);
        } finally {
            executor.shutdown();
        }
    }

    private Runnable createTask(String client, String password, CountDownLatch latch) {
        return () -> {
            try {
                latch.countDown();
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            AccessMap.get().verifyDetails(client, password);
        };
    }

    // TODO when updating to java 19+ this whole method can be replaced with await().until(() -> task.state() == DONE)
    private void assertDone(Future<?> task) {
        // waits until some form of completion
        await().until(task::isDone);
        // makes sure that the completion wasn't an error
        assertDoesNotThrow(() -> task.get());
    }

    @Test
    void test_multiple_clients_with_different_passwords_valid_and_invalid() {
        var clientMap = AccessMap.get();
        var accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Client1");
        accessDetails.setTopics(List.of(new AccessTopics("Topic-1", null)));
        var accessAPI = new AccessAPI();
        accessAPI.setHashed_key("f9e32f751ea86df7e9b281ca618def7a8551970660e3e0a361dd93b4d6ec6ed1");
        accessAPI.setSalt("This-is-a-test-salt");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        clientMap.add("Client1", accessDetails);

        accessDetails = new AccessDetails();
        accessDetails.setRegistered_client("Client2");
        accessDetails.setTopics(List.of(new AccessTopics("Topic-2", null)));
        accessAPI = new AccessAPI();
        accessAPI.setHashed_key("f89e6652be40b81120cdd2507bed9a3aeed41f3f22cbdd329494997eb5fb523b");
        accessAPI.setSalt("This-is-a-test-salt");
        accessAPI.setIssued("issued_date");
        accessAPI.setRevoked(false);
        accessDetails.setApi(accessAPI);
        clientMap.add("Client2", accessDetails);

        assertDoesNotThrow(() -> clientMap.verifyDetails("Client1", "This-is-the-client1-test-password"));
        assertThrowsExactly(
                AccessDeniedException.class,
                () -> clientMap.verifyDetails("Client2", "This-is-the-client2-INCORRECT-test-password"));
    }

    @Test
    void test_add_happyPath() {
        // given
        AccessMap map = AccessMap.get();
        int size = map.size();
        // when
        map.add("AddEntry", new AccessDetails());
        // then
        assertEquals(size + 1, map.size());
    }

    @Test
    void test_remove_happyPath() {
        // given
        AccessMap map = AccessMap.get();
        map.add("RemoveEntry", new AccessDetails());
        int size = map.size();
        // when
        map.remove("RemoveEntry");
        // then
        assertEquals(size - 1, map.size());
    }

    @Test
    void test_update_add() {
        // given
        AccessMap map = AccessMap.get();
        int size = map.size();
        // when
        AccessDetails expectedDetails = new AccessDetails();
        expectedDetails.setRegistered_client("Existing_Client");
        map.update("NoMatchingEntryUpdate", expectedDetails);

        // then
        AccessDetails actualDetails = map.getDetails("NoMatchingEntryUpdate");
        assertEquals(expectedDetails, actualDetails);
        assertEquals(size + 1, map.size());
    }

    @Test
    void test_update_overwrite() {
        // given
        AccessMap map = AccessMap.get();
        map.add("UpdateEntry", new AccessDetails());

        // when
        AccessDetails expectedDetails = new AccessDetails();
        expectedDetails.setRegistered_client("Existing_Client");
        map.update("UpdatedEntry", expectedDetails);

        // then
        AccessDetails actualDetails = map.getDetails("UpdatedEntry");
        assertEquals(expectedDetails, actualDetails);
    }

    @Test
    void test_size_emptyMap() {
        AccessMap map = AccessMap.get();
        map.clear();
        assertEquals(0, map.size());
    }

    @Test
    void test_size_withEntries() {
        AccessMap map = AccessMap.get();
        map.clear();
        map.add("testKey", new AccessDetails("client", null, null, null, null));
        assertEquals(1, map.size());
    }

    @Test
    void test_isEmpty_emptyMap() {
        AccessMap map = AccessMap.get();
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void test_isEmpty_withEntries() {
        AccessMap map = AccessMap.get();
        map.clear();
        map.add("testKey", new AccessDetails("client", null, null, null, null));
        assertFalse(map.isEmpty());
    }

    @Test
    void test_clear() {
        AccessMap map = AccessMap.get();
        map.add("testKey", new AccessDetails("client", null, null, null, null));
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void test_getClientNames_emptyMap() {
        AccessMap map = AccessMap.get();
        map.clear();
        assertTrue(map.getClientNames().isEmpty());
    }

    @Test
    void test_getClientNames_withEntries() {
        AccessMap map = AccessMap.get();
        map.clear();
        map.add("testKey1", new AccessDetails("client1", null, null, null, null));
        map.add("testKey2", new AccessDetails("client2", null, null, null, null));

        var clientNames = map.getClientNames();
        assertEquals(2, clientNames.size());
        assertTrue(clientNames.contains("testKey1"));
        assertTrue(clientNames.contains("testKey2"));
    }

    @Test
    void test_init_from_file() {
        // given
        ClassLoader classLoader = getClass().getClassLoader();
        File f = new File(classLoader.getResource("access-map.json").getFile());
        assertDoesNotThrow(() -> AccessMap.initFromFile(f));
        AccessMap map = AccessMap.get();

        // when
        // then
        assertDoesNotThrow(() -> map.verifyDetails("Key1", "hashed_key"));
    }
}
