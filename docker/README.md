# Running the Federator using Docker

**Repository:** `federator`  
**Description:** `how to run the federator using docker`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

# Docker Compose Running & Testing

The **federator/docker** directory contains all the docker and application configuration required to run the various different
components that make up the federator service.  This configuration typically consists of a server(s) and client(s).

The solution will take data from a source Kafka instance, filter the data and then write the data to a target Kafka instance.  
The filter is a simple exact match filter that uses the securityLabel data that form the headers for Kafka messages.

### Docker Configuration Files

The system uses the following docker yaml configuration files to build the containers and then run them with different settings:

- [**docker-compose-multiple-clients-multiple-server.yml**](docker-compose-multiple-clients-multiple-server.yml) - This file will run the service with multiple clients and multiple servers.  It will start both the server and client containers as well as the shared resources.
- [**docker-compose-multiple-clients-single-server.yml**](docker-compose-multiple-clients-single-server.yml) - This file will run the service with multiple clients and a single server.  It will start both the server and client containers as well as the shared resources.
- [**docker-compose-grpc-single-client-multi-server.yml**](docker-compose-grpc-single-client-multi-server.yml) - This file will run the service with a single client and multiple servers.  It will start both the server and client containers as well as the shared resources.
- [**docker-compose-grpc.yml**](docker-compose-grpc.yml) - This file will run the gRPC solution.  This solution will run two instances of Kafka and Zookeeper.  The solution was designed to be a simple demonstration of the federator service.

### Application configuration files

#### Server Configuration

See [server configuration](../docs/server-configuration.md) for more details.

#### Client Configuration

See [client configuration](../docs/client-configuration.md) for more details.

#### Authentication Configuration

See [authentication configuration](../docs/authentication.md) for more details.


### Logging configuration

See [logging configuration](docs/logging-configuration.md) for more details.

### Running the Service

#### Running Docker

Make sure you [set the project up locally](../docs/running-locally.md) first.

Running docker compose using a yaml file (for example [**docker-compose-grpc.yml**](docker-compose-grpc.yml)) will create
and run the containers which will in turn create test topics and then populate them a simple set of test messages that consist of RDF triples.

To run the current solution ensure that you have built the jar files and the docker images for the client and server.
The supporting data and as is configuration will run this without any modification.

#### Building the Docker Containers

To build the docker containers for the gRPC solution run the following command from the project root directory:

```shell
docker compose --file docker/docker-compose-grpc.yml build
```

To build the docker containers for the multiple clients and multiple servers solution run the following command:

```shell
docker compose --file docker/docker-compose-multiple-clients-multiple-server.yml build
```

The other configurations can be built by using their yaml docker-compose file.

#### Running the Docker Containers

To start and run the docker containers for the gRPC solution run the following command:

```shell
docker compose --file docker/docker-compose-grpc.yml up
```

To start and run the docker containers for the multiple clients and multiple servers solution run the following command:

```shell
docker compose --file docker/docker-compose-multiple-clients-multiple-server.yml up
```

The other configurations can be run by using their yaml docker-compose file.

#### Stopping the Docker Containers

To stop the docker containers for the gRPC solution run the following command:

```shell
docker compose --file docker/docker-compose-grpc.yml down
```

To stop the docker containers for the multiple clients and multiple servers solution run the following command:

```shell
docker compose --file docker/docker-compose-multiple-clients-multiple-server.yml down
```

The other configurations can be stopped by using their yaml docker-compose file.

#### Stopping and Removing the Docker Containers

To stop and remove the docker containers add the `--volumns` option to the down command.
For example for the gRPC solution run the following command:

```shell
docker compose --file docker/docker-compose-grpc.yml down --volumes
```

#### Monitoring the Docker Containers

To confirm the docker containers are running you can run the following command to see the container processes:

```shell
docker ps
```

To further confirm that the docker containers are running correctly you can inspect the logs for the containers.  For example to inspect the logs for the federator-server container run the following command:

```shell
docker compose --file docker/docker-compose-grpc.yml logs federator-server
```

#### Inspecting the Kafka Topics and Messages

To view the topics and/or messages you can use the Kafka command line tools.  
However since everything is running inside of containers we first need to get a shell into the container.

To get a shell into the Kafka container run the following command (for the kafka-src container):

```shell
docker exec -it kafka-src /bin/bash
```

From here you should see a bash prompt prefixed with the user and container id.  
You can now run the Kafka command line tools.

To see the topics that have been created in the Kafka instance run the following command:

```shell
kafka-topics --list --bootstrap-server localhost:19093
```

To see the messages in a topic run the following command:

```shell
kafka-console-consumer --bootstrap-server localhost:19093 --topic knowledge --from-beginning --property print.headers=true
```

To exit the docker container shell run the following command:

```shell
exit  
```

It is possible to run the Kafka command without having to enter the container shell.  For example to see the topics that have been created in the Kafka instance run the following command:

```shell
docker exec -it kafka-src kafka-topics --list --bootstrap-server localhost:19093
```

To see the messages in a topic run the following command:

```shell
docker exec -it kafka-src kafka-console-consumer --bootstrap-server localhost:19093 --topic knowledge --from-beginning --property print.headers=true
```

To check to see the topics that have been created in the Kafka target instance run the following command:

```shell
docker exec -it kafka-target kafka-topics --list --bootstrap-server localhost:29093
```

To check to see the messages in the federated-knowledge topic on the target server run the following command:

```shell
docker exec -it kafka-target kafka-console-consumer --bootstrap-server localhost:29093 --topic federated-knowledge --from-beginning --property print.headers=true
```

### Shared Resources

The shared resources are included in the other configuration files to provide utility functions used for testing and the running of the service.
The docker compose solutions use environmental properties that are picked up from the [**.env**](.env) file.
Please see the comments inside this file that outline what can be easily altered.

- [**docker-grpc-resources/docker-compose-shared.yml**](docker-grpc-resources/docker-compose-shared.yml).
- This yaml file defines the following containers:
  - **zookeepers** - the management tools for Kafka (zookeeper-src and zookeeper-target).  These are containers that run that Kafka management service instances for the source and target.

    - **zookeeper-src properties**

      |                     Property                     |                      Description                       |
      |--------------------------------------------------|--------------------------------------------------------|
      | `KAFKA_ADVERTISED_LISTENERS`                     | The advertised listeners for the Kafka instance        |
      | `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`           | The security protocol map for the Kafka instance       |
      | `KAFKA_INTER_BROKER_LISTENER_NAME`               | The inter broker listener name for the Kafka instance  |
      | `KAFKA_ZOOKEEPER_CONNECT`                        | The zookeeper connection string for the Kafka instance |
      | `KAFKA_BROKER_ID`                                | The broker id for the Kafka instance                   |
      | `KAFKA_LOG4J_LOGGERS`                            | The loggers for the Kafka instance                     |
      | `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`         | The replication factor for the Kafka instance          |
      | `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | The replication factor for the Kafka instance          |
      | `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR`            | The minimum ISR for the Kafka instance                 |
      | `KAFKA_JMX_PORT`                                 | The JMX (management) port for the Kafka instance       |
      | `KAFKA_JMX_HOSTNAME`                             | The JMX hostname for the Kafka instance                |

    - **zookeeper-target properties**

      |        Property         |                    Description                     |
      |-------------------------|----------------------------------------------------|
      | `ZOOKEEPER_CLIENT_PORT` | The port that the zookeeper client will connect to |
      | `ZOOKEEPER_SERVER_ID`   | A unquiie id for the server                        |
      | `ZOOKEEPER_SERVERS`     | The zookeeper server list                          |

  - **kafka-src** - the source Kafka instance where the original messages will be published to

    |                     Property                     |                      Description                       |
    |--------------------------------------------------|--------------------------------------------------------|
    | `KAFKA_ADVERTISED_LISTENERS`                     | The advertised listeners for the Kafka instance        |
    | `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`           | The security protocol map for the Kafka instance       |
    | `KAFKA_INTER_BROKER_LISTENER_NAME`               | The inter broker listener name for the Kafka instance  |
    | `KAFKA_ZOOKEEPER_CONNECT`                        | The zookeeper connection string for the Kafka instance |
    | `KAFKA_BROKER_ID`                                | The broker id for the Kafka instance                   |
    | `KAFKA_LOG4J_LOGGERS`                            | The loggers for the Kafka instance                     |
    | `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`         | The replication factor for the Kafka instance          |
    | `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | The replication factor for the Kafka instance          |
    | `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR`            | The minimum ISR for the Kafka instance                 |
    | `KAFKA_JMX_PORT`                                 | The JMX (management) port for the Kafka instance       |
    | `KAFKA_JMX_HOSTNAME`                             | The JMX hostname for the Kafka instance                |

  - **kafka-target** - the target Kafka instance where processed messages will be published to

    |                     Property                     |                      Description                       |
    |--------------------------------------------------|--------------------------------------------------------|
    | `KAFKA_ADVERTISED_LISTENERS`                     | The advertised listeners for the Kafka instance        |
    | `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`           | The security protocol map for the Kafka instance       |
    | `KAFKA_INTER_BROKER_LISTENER_NAME`               | The inter broker listener name for the Kafka instance  |
    | `KAFKA_ZOOKEEPER_CONNECT`                        | The zookeeper connection string for the Kafka instance |
    | `KAFKA_BROKER_ID`                                | The broker id for the Kafka instance                   |
    | `KAFKA_LOG4J_LOGGERS`                            | The loggers for the Kafka instance                     |
    | `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`         | The replication factor for the Kafka instance          |
    | `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | The replication factor for the Kafka instance          |
    | `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR`            | The minimum ISR for the Kafka instance                 |
    | `KAFKA_JMX_PORT`                                 | The JMX (management) port for the Kafka instance       |
    | `KAFKA_JMX_HOSTNAME`                             | The JMX hostname for the Kafka instance                |

  - **redis** - a cache used to store the message offset of the last message processed for a topic

  - **kafka-topics-creator** - a container that will create three test topics prefixed with 'knowledge'

    |     Property      |                        Description                        |
    |-------------------|-----------------------------------------------------------|
    | `BOOTSTRAP_VALUE` | The broker server in which the topics are to be created   |
    | `KAFAK_TOPICS`    | The names of topics to be created, a space seperated list |

  - **kafka-topics-populator** - a container that will populate the 'knowledge' topics with messages taken from a file in the 'input' directory

    |       Property        |                        Description                        |
    |-----------------------|-----------------------------------------------------------|
    | `KAFKA_BROKER_SERVER` | The broker server in which the topics are to be populated |
    | `KNOWLEDGE_TOPIC`     | The name of the topics to be populated                    |
    | `KNOWLEDGE_DATA`      | The name of the files containing the messages to be sent  |
    | `DATA_DELINEATOR`     | The character that deliniates the messages in the file    |

  - **kafka-test-data-producer** - a container that will generate and then populate topics with random test data for testing purposes

    |           Property            |                           Description                           |
    |-------------------------------|-----------------------------------------------------------------|
    | `KAFKA_BROKER_SERVERS`        | The broker server(s) in which the topics are to be populated    |
    | `KAFAK_TOPICS`                | The names of topics to be created, a space seperated list       |
    | `COUNTRY_CODES`               | The 'seed' country codes to be used in the test data generation |
    | `COMPANY_NAMES`               | The 'seed' company names to be used in the test data generation |
    | `SURNAMES`                    | The 'seed' surnames to be used in the test data generation      |
    | `FIRST_NAMES`                 | The 'seed' first names to be used in the test data generation   |
    | `DELAY_BETWEEN_DATA_LOADS`    | The delay between data loads in seconds                         |
    | `NUMBER_OF_DATA_LOADS`        | The number of data loads to be performed (0 to switch off)      |
    | `NUMBER_OF_MESSAGES_PER_LOAD` | The number of messages to be generated and sent per data load   |
    | `DATA_DELINEATOR`             | The character that deliniates the generated messages            |

  - **kafka-bulk-test-data-loader** - a container that will populate topics with bulk test data from a file for testing purposes

    |          Property          |                         Description                          |
    |----------------------------|--------------------------------------------------------------|
    | `KAFKA_BROKER_SERVERS`     | The broker server(s) in which the topics are to be populated |
    | `KAFAK_TOPICS`             | The names of topics to be created, a space seperated list    |
    | `DATA_FILENAME`            | The name of the file containing the messages to be sent      |
    | `DATA_DELINEATOR`          | The character that delineates the messages in the file       |
    | `DELAY_BETWEEN_DATA_LOADS` | The delay between data loads in seconds                      |
    | `NUMBER_OF_DATA_LOADS`     | The number of data loads to be performed (0 to switch off)   |

  - **network** (not a container) - a yaml configuration to set up an internal network `core` for the containers to communicate with each other

## Smoke Tests

* [Single Client with Single Server](../docs/smoke-tests/one-to-one.md)

### Multiple Client/Server Setups

[//]: # (The following multiple client/server section will be replaced with more up to date instructions on running the
smoke tests, possibly in a similar format to the changes above - See IA-842-844)

Smoke tests have also been enabled via docker-compose for each of the following use cases:
- Single Client with Multiple Servers
- Multiple Clients with Single Servers
- Multiple Clients with Multiple Server

Test data will be generated and loaded via the docker-compose-share service, which is included in the individual Docker Compose files. A bulk data producer is there as well, but it may take a few minutes to generate the test data.

Each corresponding Docker Compose setup includes checks for the expected number of messages in the respective Kafka broker and topics. Run the following docker-compose commands:

```shell
docker compose --file docker/docker-compose-grpc-single-client-multi-server.yml up -d   
```

```shell
docker compose --file docker/docker-compose-multiple-clients-multiple-server.yml up -d  
```

```shell
docker compose --file docker/docker-compose-multiple-clients-single-server.yml up -d
```

To verify that the smoke tests have passed, review the logs of the kafka-message-counter and kafka-message-counter-2 (if applicable) services.

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
