# Deploying a Federator Locally

**Repository:** `federator`  
**Description:** `deploying the federator locally.`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## A Guide showing the steps on deploying a federator locally.

## Table of Contents

- [Introduction](#introduction)
- [Federator Deployment](#federator-deployment)
- [Changing Topics (Optional)](#changing-topics-optional)
- [Additional Test Data (Optional)](#additional-test-data-optional)

## Introduction

The following guide shows how to deploy a federator locally that can be connected to two IA Nodes.

This guide will assume you have already followed the setup in [Running the federator locally](running-locally.md) and
have already set up your maven `.m2/settings.xml` profile and have a working PAT (Personal Access Token).

## Federator Deployment

1. Locate the `docker/docker-compose-grpc.yml` file which contains a federator
   client and server.

2. Replace the `image` of the `federator-server` and `federator-client` with their respective GHCR images, we recommend
   checking the [released federator packages](https://github.com/orgs/National-Digital-Twin/packages?repo_name=federator)
   for an up-to-date version.

   ```yaml
    federator-server:
    #    image: uk.gov.dbt.ndtp/${ARTIFACT_ID}-server:${VERSION}
        image: ghcr.io/national-digital-twin/federator/federator-server:0.90.0

   ... Rest of file ...

   federator-client:
   #  image: uk.gov.dbt.ndtp/${ARTIFACT_ID}-client:${VERSION}
      image: ghcr.io/national-digital-twin/federator/federator-client:0.90.0
   ```
3. Start the docker containers with the following command:

   ```bash
   docker compose --file docker/docker-compose-grpc.yml up -d
   ```
4. Wait for the services to start and data to be loaded into the federated Kafka topic.
5. Check federated messages have been filtered in Kafka with the following Kafka consumer command:

   ```bash
   ./kafka-console-consumer.sh --bootstrap-server localhost:29093 --topic federated-client1-FederatorServer1-knowledge --from-beginning
   ```

## Changing Topics (Optional)

This sections shows how to add another topic to the federator 'stack' to choose where data is sent
and then federated.

1. Locate the `docker/docker-grpc-resources/docker-compose-shared.yml` file which contains the source
   and target kafka topics, and some helpful shell scripts to add and remove data.

2. On line 135, add `RDF` to the end:

   ```yaml
   environment:
     BOOTSTRAP_VALUE: "kafka-src:19092"
     KAFKA_TOPICS: "knowledge knowledge1 knowledge2 RDF"
   ```


## Additional Test Data (Optional)

This sections shows how to add additional test data to a new topic that can be monitored by
the federator.

1. Like [changing topics](#changing-topics-optional) Locate the `docker/docker-grpc-resources/docker-compose-shared.yml` file

2. Update line 152 by adding the `RDF` Topic and `${KNOWLEDGE_DATA_3}`:

   ```yaml
   environment:
    KAFKA_BROKER_SERVER : "kafka-src:19092"
    KNOWLEDGE_TOPIC: "knowledge knowledge1 knowledge2 RDF"
    KNOWLEDGE_DATA: "${KNOWLEDGE_DATA} ${KNOWLEDGE_DATA_1} ${KNOWLEDGE_DATA_2} ${KNOWLEDGE_DATA_3}"
   ```
3. Update `.env` file located in `/docker/.env` with the following sample data:

   ```plaintext
   KNOWLEDGE_DATA_3=simple-sample-test3.dat
   ```
4. Create a new `simple-sample-test3.dat` file containing triples data and an optional `Security-Label` header
   in `docker/input` (or copy `simple-sample-test2.dat` and rename the file).
5. Start your containers with the following docker compose command:

   ```bash
   docker compose --file docker/docker-compose-grpc.yml up -d
   ```
6. Check the federated messages on your new topic:

   ```bash
   ./kafka-console-consumer.sh --bootstrap-server localhost:29093 --topic federated-client1-FederatorServer1-RDF --from-beginning
   ```

   or your un-federated messages on the source kafka topic:

   ```bash
   ./kafka-console-consumer.sh --bootstrap-server localhost:19093 --topic RDF --from-beginning
   ```

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
