# Running the Federator locally

**Repository:** `federator`  
**Description:** `running the federator locally`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Outlines how to install, build, and run the Federator client and server locally.

## Table of Contents

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Certificates & Security](#certificates--security)
- [Configuration](#configuration)
- [Management Node & Keycloak Integration](#management-node--keycloak-integration)
- [Maven setup](#maven-setup)
- [Compiling and Running](#compiling-and-running)
- [Viewing the Kafka Topics](#viewing-the-kafka-topics)
- [Troubleshooting](#troubleshooting)
- [Further Examples](#further-examples)

## Introduction

The following outlines how to take the java code and run the application locally using resources that are run within Docker containers. These docker containers will start up the Kafka, Zookeeper, and Redis resources.

For more information on how to run the docker containers see the [docker readme](../docker/README.md).

### Requirements

This will require: Java (version 21+), Docker, and Git to be installed.
Docker desktop is the preferred tool, however a vanilla Docker installation will also work.
If you are an Ubuntu user then you might also need to follow some extra steps to get Docker Desktop working [see the link here.](https://docs.docker.com/desktop/get-started/#credentials-management-for-linux-users)

Additionally, you must have:
- [Management Node](https://github.com/National-Digital-Twin/management-node) running and accessible. This is a Spring Boot application and must be started for configuration and coordination.
- [Keycloak](https://www.keycloak.org/) instance for authentication and JWT issuance.
- [Postgres] database for Keycloak, typically started via the Management Node Docker Compose file.

### Certificates & Security

Federator uses mTLS for all secure communication between client, server, Management Node, and Keycloak. You must:
- Generate and configure valid certificates for all components (client, server, Management Node, Keycloak).
- Use PKCS12 (`.p12`) format for keystore files.
- Use JKS (`.jks`) format for truststore files.
- Ensure the keystore and truststore are correctly referenced in your properties files for both client and server.
- Certificates must be trusted by all parties to establish secure connections.
- The same certificates are used for mTLS authentication to Keycloak and Management Node, and for producer-consumer communication.

> **Note:** If certificates are invalid or not trusted, mTLS connections will fail and configuration/authentication will not work.

For details on generating and configuring certificates, see the [authentication.md](authentication.md) documentation.

### Configuration

Please see the following links for configuration details:
- [server configuration](server-configuration.md) for more details.
- [client configuration](client-configuration.md) for more details.
- [authentication configuration](authentication.md) for more details.
- [logging configuration](docs/logging-configuration.md) for more details.

#### Key Properties

- **client.properties**: Main client configuration. Location defined by the `FEDERATOR_CLIENT_PROPERTIES` environment variable.
- `common.configuration`: Path to the common configuration file, set in `client.properties` (e.g., `common.configuration=src/configs/common-configuration.properties`).
- `management.node.base.url`: URL of the Management Node (e.g., `https://localhost:8090`).
- `management.node.request.timeout`: Timeout for Management Node requests.
- `management.node.cache.ttl.seconds`: Cache TTL for Management Node responses.

> **Note:** Properties cannot be overridden by environment variables. All configuration must be set in the properties files.

### Management Node & Keycloak Integration

Federator requires Management Node for dynamic configuration and Keycloak for authentication. The client:
- Uses mTLS for secure communication with both Management Node and servers.
- Obtains a JWT token from Keycloak (using mTLS).
- Uses the JWT token to authenticate with Management Node and retrieve configuration (including producer/server details).
- The Management Node returns configuration as JSON, which the client uses to connect to servers and manage jobs.
- The server validates JWT tokens and checks the `aud` claim for authorization.

To spin up Management Node, Keycloak, and Postgres locally, use the Docker Compose file provided by the Management Node project: [Management Node Docker Compose](https://github.com/National-Digital-Twin/management-node/tree/main/docker/keycloak).

For more details, see [authentication.md](authentication.md).

### Maven setup

Currently, we are using GitHub package repositories to distribute libraries. To ensure that Maven have access to the package repositories we need to update the settings.
Instructions on how to do this can be found [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) which will contain additional information.

You will need to generate a [personal access token](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-with-a-personal-access-token) that has access to at least reading package registries.
Open up `$MAVEN_HOME/.m2/settings.xml` and add the following

```xml
<settings>
    <activeProfiles>
        <activeProfile>github</activeProfile>
    </activeProfiles>

    <!--  Optional as this will also be defined in the pom file  -->
    <profiles>
        <profile>
            <id>github</id>
            <repositories>
                <repository>
                    <id>central</id>
                    <url>https://repo1.maven.org/maven2</url>
                </repository>
                <repository>
                    <id>github</id>
                    <url>https://maven.pkg.github.com/National-Digital-Twin/*</url>
                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>
                </repository>
            </repositories>
        </profile>
    </profiles>

    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Compiling and Running

#### Compile and build the JAR file

- From a terminal window navigate to the root directory of the project.  Run the following command to compile the code.

```shell
./mvnw clean install
```

You should now have built two JAR files:
- `federator-server-X.X.X.jar` (server)
- `federator-client-0.3.0.jar` (client)

#### Running Docker Compose

To start the federators common docker services run the following command:

```shell
docker compose --file docker/docker-grpc-resources/docker-compose-shared.yml up -d
```

- This will create the kafka source `kafka-src` and target `kafka-target` together with two zookeeper and one redis containers.
- It will also create four additional containers to create the test topics and populate them with test data.

To confirm that the containers are running, check [this guide](../docker/README.md/#monitoring-the-docker-containers):

#### Running the Federator Server Code

Start the federation server code. Note the parameters that are being passed based on properties to tell it the access
topics, the Kafka server instance location and port. The remaining properties are picked up from the default properties
file bundled into the Jar file.

- Export the properties file location to the environment variable `FEDERATOR_SERVER_PROPERTIES`

```shell
export FEDERATOR_SERVER_PROPERTIES=./src/configs/server.properties
```

This will configure the server to use the properties file [server.properties](../src/configs/server.properties) 

- Start the server code contained in a jar file:

```shell
java -jar ./target/federator-server-0.3.0.jar
```

#### Running the Federator Client Code

- Export the properties file location to the environment variable `FEDERATOR_CLIENT_PROPERTIES`

```shell
export FEDERATOR_CLIENT_PROPERTIES=./src/configs/client.properties 
```

This will set the client to use the properties file [client.properties](../src/configs/client.properties) and the related common configuration file as defined by the `common.configuration` property.

> **Note:** The client now retrieves its connection configuration dynamically from the Management Node. The previous `connection-configuration.json` is no longer used.

- Start the client code contained in a jar file:

```shell
java -jar ./target/federator-client-0.3.0.jar
```

#### Stopping the Client and Server Code

- To stop either of the client or server code use `ctrl c` in the terminal window where the code is running.

#### Stopping the Docker Containers

- Stop the docker containers with the commands in the [docker docs](../docker/README.md/#stopping-the-docker-containers)

#### Viewing the Kafka Topics

#### Viewing the Kafka Topics on the Target Kafka Instance

- To view the processed Kafka topics on the target Kafka instance use the following command:

```shell
docker exec -it kafka-target kafka-console-consumer --bootstrap-server localhost:29093 --topic federated-Localhost-createDataTopic1 --from-beginning --property print.headers=true
```

- This will show the messages that have been processed by the federator client code.
- The topic name will be prefixed with `federated-` and the original topic name (`federated-Localhost-createDataTopic1`).

#### Viewing the Kafka Topics on the Source Kafka Instance

- To view the Kafka topics on the source Kafka instance use the following command:

```shell
docker exec -it kafka-src kafka-console-consumer --bootstrap-server localhost:19093 --topic createDataTopic1 --from-beginning --property print.headers=true
```

- This will show the test messages that have been loaded into the source Kafka instance.

#### Smoke Tests

- To run smoke tests, you will need to [set up the compose files in this guide](../docker/README.md/#smoke-test)

### Troubleshooting

- Ensure Management Node, Keycloak, and Postgres are running and accessible from Federator.
- Check mTLS certificates, keystore, and truststore configuration for all components.
- Verify that the `common.configuration` property in `client.properties` points to the correct file.
- Properties must be set in the properties files and cannot be overridden by environment variables.
- If you encounter issues with authentication or configuration retrieval, check certificate validity and trust relationships.

### Further Examples

Further example configurations can be found in the [configs readme](../src/configs/README.md).

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
