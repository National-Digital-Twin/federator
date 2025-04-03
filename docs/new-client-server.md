# Federator Configuration

**Repository:** `federator`  
**Description:** `configuring the federator client and/or server`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Outlines how to add and configure a new server (Producer) or client (Consumer) for the Federator.

Adding and configuring a server or client for the Federator involves 3 main steps.

- Step 1: Amending the docker configuration file
- Step 2: Adding a new server properties file
- Step 3: Adding a new access file

Let's look at an example using a many-to-many (many servers to many clients) configuration as detailed in the [docker-compose-multiple-clients-multiple-server.yml](../docker/docker-compose-multiple-clients-multiple-server.yml) file.

## Prerequisites

- A running instance of the Federator
- Access to the configuration files for the Federator

## Adding a new Server

You can add a new server to the Federator by following these steps:

### Step 1: Amend the docker configuration file

- Add a new server to the docker configuration (yaml) file. For example, to add a new server called `federator-server-new-server` to the `docker-compose-multiple-clients-multiple-server.yml` file:

````yaml
federator-server-new-server:
  image: uk.gov.dbt.ndtp/${ARTIFACT_ID}-server:${VERSION}
  container_name: federator-server-new-server
  environment:
    FEDERATOR_SERVER_PROPERTIES: /config/server.properties
  ports:
    - "8084:8084"
  networks:
    - core
  depends_on:
    kafka-src:
      condition: service_healthy
    kafka-topics-populator:
      condition: service_completed_successfully
  volumes:
    - ./docker-grpc-resources/multiple-clients-multiple-server/access-new-server.json:/config/access.json
    - ./docker-grpc-resources/multiple-clients-multiple-server/server-new-server.properties:/config/server.properties
    - ./filter:/library
  restart: on-failure
````

This new server will listen and then connect with clients using port 8084 on the localhost

> The 2 configuration files are mounted as volumes.

### Step 2: Add a new server properties file for the new server

- Add a new server configuration file in the `docker-grpc-resources/multiple-clients-multiple-server` directory. For example, to add a new server configuration file called `server-new-server.properties`:

````properties
kafka.bootstrapServers=kafka-src:19092
kafka.defaultKeyDeserializerClass=org.apache.kafka.common.serialization.StringDeserializer
kafka.defaultValueDeserializerClass=uk.gov.dbt.ndtp.federator.access.AccessMessageDeserializer
kafka.consumerGroup=server.consumer
kafka.pollDuration=PT10S
kafka.pollRecords=100

shared.headers=Security-Label^Content-Type

filter.shareAll=false

## only port is different
server.port=8084
server.tlsEnabled=false
server.keepAliveTime=10
server.accessMapValueFile=/config/access.json
````

> `server.accessMapValueFile=/config/access.json` will access the mounted file specified in the above docker volume configuration.

### Step 3: Add a new access file for the new server

- Add a new access file called `access-new-server.json` in the `docker-grpc-resources/multiple-clients-multiple-server` directory.

```json
{
  "ndtp.new-client": {
    "registered_client": "ndtp.dbt.gov.uk.new-client",
    "filter_classname": "",
    "topics": [
      {
        "name": "new-example-topic-1",
        "granted_at": "2023-05-09T14:45:25.371402+01:00"
      },
      {
        "name": "new-example-topic-2",
        "granted_at": "2023-05-09T14:45:25.371402+01:00"
      }
    ],
    "api": {
      "hashed_key": "<SHA3_HASH_VALUE_GIVEN_CLIENT_PASSWORD_POSTFIX_BY_THE_SALT_VALUE",
      "salt": "<SALT_VALUE>",
      "issued": "2023-05-09T14:43:37.724246+01:00",
      "revoked": false
    },
    "attributes": {
      "nationality": "GBR",
      "clearance": "O",
      "organisation_type": "NON-GOV3"
    }
  }
}
```

- The above server configuration file will allow a client named `ndtp.new-client` to connect to this new federator server and access the topics `new-example-topic-1` and `new-example-topic-2`.
- This will filter the kafka messages using their header data based on the attributes `nationality`, `clearance` and `organisation_type`.
- The `hashed_key` is the SHA3 hash value of the client password with the salt value appended to it.  See the guide on [authentication](authentication.md) for more information.

## Adding a new Client

You can add a new client to the Federator by following these steps:

### Step 1: Amend the docker configuration file

- Add a new client to the docker configuration (yaml) file.
- For example to add a new client called `federator-client-new-server` to the `docker-compose-multiple-clients-multiple-server.yml` file:

````yaml
federator-client-new-client:
  image: uk.gov.dbt.ndtp/${ARTIFACT_ID}-client:${VERSION}
  container_name: federator-client-new-client
  environment:
    FEDERATOR_CLIENT_PROPERTIES: /config/client.properties
  networks:
    - core
  volumes:
    - ./docker-grpc-resources/multiple-clients-multiple-server/client-new-client.properties:/config/client.properties
    - ./docker-grpc-resources/multiple-clients-multiple-server/connection-configuration-new-client.json:/config/connection-configuration.json
  depends_on:
    redis:
      condition: service_healthy
    kafka-target-2:
      condition: service_healthy
    kafka-topics-populator:
      condition: service_completed_successfully
    federator-server-1:
      condition: service_started
  restart: on-failure
````

> The 2 configuration files are mounted as volumes.

### Step 2: Add a new client properties file for the new client

- Add a new client configuration file in the `docker-grpc-resources/multiple-clients-multiple-server` directory.
  For example, to add a new client configuration file called `client-new-client.properties`:

````properties
kafka.sender.defaultKeySerializerClass=org.apache.kafka.common.serialization.BytesSerializer
kafka.sender.defaultValueSerializerClass=org.apache.kafka.common.serialization.BytesSerializer
kafka.bootstrapServers=kafka-target:19092

## kafka.topic.prefix default is empty string
kafka.topic.prefix=federated
kafka.consumerGroup=ndtp.dbt.gov.uk.1
## Default redis.host is localhost
redis.host=redis
## Default redis.port is 6379
redis.port=6379
## Default redis.tls.enabled empty value "" = false, missing property entry = true
redis.tls.enabled=false
connections.configuration=/config/connection-configuration.json
````

### Step 3: Add a new connection configuration file for the new client

- Add a new connection configuration file in the `docker-grpc-resources/multiple-clients-multiple-server` directory.

```json
[
    {
        "credentials": {
            "name": "ndtp.new-client",
            "key": "This-is-a-password-for-a-client"
        },
        "server": {
            "name": "FederatorServerName",
            "host": "<SERVER-HOST-NAME>",
            "port": 8084
        },
        "tls": {
            "enabled": false
        }
    }
]
```

> The `name` field in the credentials section must match the (top level json) client name in the access file for the server. The `key` field is the password for the client.

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
