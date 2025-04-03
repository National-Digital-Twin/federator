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

package uk.gov.dbt.ndtp.federator.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import uk.gov.dbt.ndtp.federator.exceptions.FilterCreationException;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

public class FilterReflectiveCreatorTest {

    @Test
    void testGetMessageFilter_Happy_Path() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterHappy";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        assertDoesNotThrow(run);
    }

    @Test
    void testGetMessageFilter_Bad_Class_Name() {
        String className = "uk.gov.dbt.ndtp.federator.utils.Rubbish";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format("Class '%s' cannot be found on classpath", className);
        assertEquals(expMsg, e.getMessage());
    }

    @Test
    void testGetMessageFilter_Class_Not_Implement_Any_Interface() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterNoInteface";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format(
                "Class '%s' does not implement interface 'uk.gov.dbt.ndtp.federator.filter.MessageFilter'", className);
        assertEquals(expMsg, e.getMessage());
    }

    @Test
    void testGetMessageFilter_Class_Not_Implements_Wrong_Interface() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterWrongInteface";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format(
                "Class '%s' does not implement interface 'uk.gov.dbt.ndtp.federator.filter.MessageFilter'", className);
        assertEquals(expMsg, e.getMessage());
    }

    @Test
    void testGetMessageFilter_Class_Implements_More_Than_One_Interface() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterManyIntefaces";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format(
                "Class '%s' implements more than interface 'uk.gov.dbt.ndtp.federator.filter.MessageFilter'",
                className);
        assertEquals(expMsg, e.getMessage());
    }

    @Test
    void testGetMessageFilter_Class_Missing_Correct_Constructor() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterBadConstructor";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format("Class '%s' does not have a constructor that takes a String", className);
        assertEquals(expMsg, e.getMessage());
    }

    // Construction flows IllegalAccessException.
    @Test
    void testGetMessageFilter_Class_Instantiation_Throws_Exception() {
        String className = "uk.gov.dbt.ndtp.federator.utils.MFTesterConstructorException";
        Executable run = () -> FilterReflectiveCreator.getMessageFilter("test-client", className);
        FilterCreationException e = assertThrows(FilterCreationException.class, run);
        String expMsg = String.format(
                "Class '%s' threw an exception when invoking the constructor that takes a String", className);
        assertEquals(expMsg, e.getMessage());
    }
}

class MFTesterHappy implements MessageFilter<KafkaEvent<?, ?>> {

    public MFTesterHappy(String s) {}

    @Override
    public boolean filterOut(KafkaEvent<?, ?> messageType) throws LabelException {
        throw new UnsupportedOperationException("Unimplemented method 'filterOut'");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
}

class MFTesterNoInteface {

    public MFTesterNoInteface(String s) {}
}

class MFTesterWrongInteface implements Closeable {

    public MFTesterWrongInteface(String s) {}

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
}

class MFTesterManyIntefaces implements Flushable, MessageFilter<KafkaEvent<?, ?>> {

    public MFTesterManyIntefaces(String s) {}

    @Override
    public boolean filterOut(KafkaEvent<?, ?> messageType) throws LabelException {
        throw new UnsupportedOperationException("Unimplemented method 'filterOut'");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'flush'");
    }
}

class MFTesterBadConstructor implements MessageFilter<KafkaEvent<?, ?>> {

    public MFTesterBadConstructor(String s, String r) {}

    @Override
    public boolean filterOut(KafkaEvent<?, ?> messageType) throws LabelException {
        throw new UnsupportedOperationException("Unimplemented method 'filterOut'");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
}

class MFTesterConstructorException implements MessageFilter<KafkaEvent<?, ?>> {

    public MFTesterConstructorException(String s) {
        throw new FilterCreationException("");
    }

    @Override
    public boolean filterOut(KafkaEvent<?, ?> messageType) throws LabelException {
        throw new UnsupportedOperationException("Unimplemented method 'filterOut'");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
}
