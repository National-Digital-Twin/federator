# Running the Federator locally

**Repository:** `federator`  
**Description:** `running the federator locally`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Outlines how to install, build, and run the Federator client and server locally.

## Table of Contents

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Configuration](#configuration)
- [Maven setup](#maven-setup)
- [Compiling and Running](#compiling-and-running)
- [Viewing the Kafka Topics](#viewing-the-kafka-topics)
- [Further Examples](#further-examples)

## Introduction

The following outlines how to take the java code and run the application locally using resources that are run within Docker containers.  These docker containers will start up the Kafka, Zookeeper, and Redis resources.

For more information on how to run the docker containers see the [docker readme](../docker/README.md).

### Requirements

This will require: Java (version 17+), Docker, and Git to be installed.
Docker desktop is the preferred tool, however a vanilla Docker installation will also work.
If you are an Ubuntu user then you might also need to follow some extra steps to get Docker Desktop working [see the link here.](https://docs.docker.com/desktop/get-started/#credentials-management-for-linux-users)

### Configuration

Please see the following links for configuration details:
- [server configuration](server-configuration.md) for more details.
- [client configuration](client-configuration.md) for more details.
- [authentication configuration](authentication.md) for more details.
- [logging configuration](docs/logging-configuration.md) for more details.

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

You should now have built both the server and client Jar files.

#### Running Docker Compose

To start the federators common docker services run the following command:

```shell
docker compose --file docker/docker-grpc-resources/docker-compose-shared.yml up -d
```

- This will create the kafka source `kafka-src` and target `kafka-target` together with two zookeeper and one redis containers.
- It will also create four additional containers to create the test topics and populate them with test data.

To confirm that the containers are running use the following command:

```shell
docker ps
```

#### Running the Federator Server Code

Start the federation server code. Note the parameters that are being passed based on properties to tell it the access
topics, the Kafka server instance location and port. The remaining properties are picked up from the default properties
file bundled into the Jar file.

- Export the properties file location to the environment variable `FEDERATOR_SERVER_PROPERTIES`

```shell
export FEDERATOR_SERVER_PROPERTIES=./src/configs/server.properties
```

This will configure the server to use the properties file [server.properties](../src/configs/server.properties) together with the
related [access.json](../src/configs/access.json) file.

- Start the server code contained in a jar file:

```shell
java -jar ./target/federator-server-0.3.0.jar
```

#### Running the Federator Client Code

- Export the properties file location to the environment variable `FEDERATOR_CLIENT_PROPERTIES`

```shell
export FEDERATOR_CLIENT_PROPERTIES=./src/configs/client.properties 
```

This will set the client to use the properties file [client.properties](../src/configs/client.properties) together with the
related [connection-configuration.json](../src/configs/connection-configuration.json) file.

- Start the client code contained in a jar file:

```shell
java -jar ./target/federator-client-0.3.0.jar
```

#### Stopping the Client and Server Code

- To stop either of the client or server code use `ctrl c` in the terminal window where the code is running.

#### Stopping the Docker Containers

- Stop the docker containers with the command below. This will also remove the volumes:

```shell
docker compose --file docker/docker-grpc-resources/docker-compose-shared.yml down -v
```

- To stop the containers without removing the volumes use the following command:

```shell
docker compose --file docker/docker-grpc-resources/docker-compose-shared.yml down
```

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

### Further Examples

Further example configurations can be found in the [configs readme](../src/configs/README.md).

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
