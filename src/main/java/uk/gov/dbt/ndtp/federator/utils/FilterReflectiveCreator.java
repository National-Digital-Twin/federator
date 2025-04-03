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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import uk.gov.dbt.ndtp.federator.exceptions.FilterCreationException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * MessageFilter created by reflection from the classpath by a single static
 * method in this class.
 * <p>
 * This approach allows different federation filters to be created and injected
 * at run time . This will allow customers to define the logic for sharing or
 * denying the federation sharing of data through the creation of custom
 * MessageFilters. The decision to share a KafkaMessage will currently be driven
 * off the KafkaHeaders only at this stage.
 */
public class FilterReflectiveCreator {

    public static final String MESSAGE_FILTER_NAME = "uk.gov.dbt.ndtp.federator.filter.MessageFilter";

    /**
     * Static method to reflectively create a MessageFilter from the className
     * provided.
     * <p>
     * This method finds the class by name, checks MessageFilter interface is
     * implemented (only) and confirms that it has a constructor that takes a String
     * before creating an instance passing clientID into the constructor. Numerous
     * exceptions can be thrown and will be wrapped by a FilterCreationException
     * with a detail debug message.
     *
     * @param clientID  passed into the filter to form the share or don't share
     *                  decision.
     * @param className full name of the class to be reflectively created.
     * @return the MessageFilter that will be applied during federation.
     * @throws FilterCreationException wraps any exceptions during the reflective
     *                                 creation.
     */
    @SuppressWarnings("java:S1452") // SonarQube rule: Remove usage of generic wildcard type.
    public static MessageFilter<KafkaEvent<?, ?>> getMessageFilter(String clientID, String className)
            throws FilterCreationException {

        // Try to get the class from the name
        Class<?> filterClass;
        try {
            filterClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            String msg = String.format("Class '%s' cannot be found on classpath", className);
            throw new FilterCreationException(msg, e);
        }

        // Check the class implements MessageFilter only
        Class<?>[] interfaces = filterClass.getInterfaces();
        if (interfaces.length == 0) {
            String msg = String.format("Class '%s' does not implement interface '%s'", className, MESSAGE_FILTER_NAME);
            throw new FilterCreationException(msg);
        }
        if (interfaces.length != 1) {
            String msg =
                    String.format("Class '%s' implements more than interface '%s'", className, MESSAGE_FILTER_NAME);
            throw new FilterCreationException(msg);
        }
        if (!interfaces[0].getName().equals(MESSAGE_FILTER_NAME)) {
            String msg = String.format("Class '%s' does not implement interface '%s'", className, MESSAGE_FILTER_NAME);
            throw new FilterCreationException(msg);
        }

        // Check it has a constructor that takes a string.
        Constructor<?> cons;
        try {
            cons = filterClass.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            String msg = String.format("Class '%s' does not have a constructor that takes a String", className);
            throw new FilterCreationException(msg, e);
        } catch (SecurityException e) {
            String msg = String.format(
                    "Class '%s' threw a security exception when getting the constructor that takes a String",
                    className);
            throw new FilterCreationException(msg, e);
        }

        // create a new instance using client name
        MessageFilter<KafkaEvent<?, ?>> filter;
        try {
            filter = (MessageFilter<KafkaEvent<?, ?>>) cons.newInstance(clientID);
        } catch (InstantiationException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {

            String msg = String.format(
                    "Class '%s' threw an exception when invoking the constructor that takes a String", className);
            throw new FilterCreationException(msg, e);
        }

        return filter;
    }
}
