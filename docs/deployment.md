# Deploying a Federator Locally

**Repository:** `federator`  
**Description:** `deploying the federator locally.`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## A Guide showing the steps on deploying a federator locally.

## Table of Contents

- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
- [Certificates & Security](#certificates--security)
- [Federator Deployment](#federator-deployment)
- [Changing Topics (Optional)](#changing-topics-optional)

## Introduction

The following guide shows how to deploy a federator locally that can be connected to two IA Nodes.

This guide will assume you have already followed the setup in [Running the federator locally](running-locally.md) and
have already set up your maven `.m2/settings.xml` profile and have a working PAT (Personal Access Token).

## Prerequisites

- Java 21+, Docker, and Git installed.
- [Management Node](https://github.com/National-Digital-Twin/management-node) (Spring Boot app) running and accessible.
- [Keycloak](https://www.keycloak.org/) instance for authentication and JWT issuance.
- [Postgres] database for Keycloak, typically started via the Management Node Docker Compose file.
- Valid certificates for mTLS:
  - Keystore in PKCS12 (`.p12`) format
  - Truststore in JKS (`.jks`) format
- All certificates must be trusted by all parties (client, server, Management Node, Keycloak).
- Docker Compose file for Management Node, Keycloak, and Postgres: [Management Node Docker Compose](https://github.com/National-Digital-Twin/management-node/tree/main/docker/keycloak)

## Certificates & Security

Federator uses mTLS for all secure communication between client, server, Management Node, and Keycloak. You must:
- Generate and configure valid certificates for all components.
- Reference keystore (.p12) and truststore (.jks) in your properties files.
- Certificates are used for mTLS authentication to Keycloak and Management Node, and for producer-consumer communication.
- JWT tokens are obtained from Keycloak and used for authorization with Management Node and servers. The server validates JWT tokens and checks the `aud` claim.

For details on generating and configuring certificates, see [authentication.md](authentication.md).

## Federator Deployment

1. Ensure Management Node, Keycloak, and Postgres are running. Use the official Management Node Docker Compose file to spin up these services:
   [Management Node Docker Compose](https://github.com/National-Digital-Twin/management-node/tree/main/docker/keycloak)

2. Locate the `docker/docker-compose-grpc.yml` file which contains a federator client and server.

3. Replace the `image` of the `federator-server` and `federator-client` with their respective GHCR images, we recommend
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
4. Start the docker containers with the following command:

   ```bash
   docker compose --file docker/docker-compose-grpc.yml up -d
   ```
5. Wait for the services to start and data to be loaded into the federated Kafka topic.
6. Check federated messages have been filtered in Kafka with the following Kafka consumer command:

   ```bash
   ./kafka-console-consumer.sh --bootstrap-server localhost:29093 --topic federated-client1-FederatorServer1-knowledge --from-beginning
   ```

> **Note:** Configuration is now dynamic and retrieved from Management Node. Ensure your `client.properties` and `server.properties` reference the correct keystore and truststore files, and set the `management.node.base.url` property.

For more details on configuration, see [client-configuration.md](client-configuration.md) and [server-configuration.md](server-configuration.md).

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

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
