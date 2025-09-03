# Client Configuration

**Repository:** `federator`  
**Description:** `how to run configure a federator client`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Provides the client configuration properties including the connection configuration.

This is defined in a properties file (`client.properties`) file whose location is defined by the `FEDERATOR_CLIENT_PROPERTIES` environment variable:

|                  Property                  |                                                                                                              Description                                                                                                              |
|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kafka.sender.defaultKeySerializerClass`   | the default key serializer class                                                                                                                                                                                                      |
| `kafka.sender.defaultValueSerializerClass` | the default value serializer class                                                                                                                                                                                                    |
| `kafka.bootstrapServers`                   | the bootstrap server for the target Kafka instance                                                                                                                                                                                    |
| `kafka.topic.prefix`                       | the prefix for the target Kafka topics                                                                                                                                                                                                |
| `kafka.consumerGroup`                      | the consumer group for the client                                                                                                                                                                                                     |
| `kafka.additional.*`                       | a repeatable optional property to set any additional kafka producer properties. These properties names must match with what kafka expects after the prefix has been removed                                                           |
| `redis.host`                               | the host for the redis cache                                                                                                                                                                                                          |
| `redis.port`                               | the port for the redis cache                                                                                                                                                                                                          |
| `redis.tls.enabled`                        | a flag to indicate if TLS is enabled for the redis cache                                                                                                                                                                              |
| `redis.username`                           | the username for authenticating connections when redis Access Control List is being used. This can be left empty if either authentication is not required, or if redis only has `requirepass` enabled
| `redis.password`                           | the password to be used for authenticating connections to redis. If either authentication is not required this can be left blank
| `redis.aes.key`                            | if set, this will be used to encrypt values stored in Redis. The value must be Base64 and decode to 16, 24, or 32 bytes.
| `connections.configuration`                | the connection configuration for the client (see `connection-configuration.json` below) - Typically this will be `/config/connection-configuration.json` as in docker we will mount different volumes/files for each client container |

#### Connection Configuration JSON

```json5
[
   {
      "credentials": { // The credentials for the client
         "name": "client-id",  // The name of the server - this maps to the server/client key value in the servers access.json file
         "key": "key" // The key or password for the client (see below)
      },
      "server": { // The server network configuration for the client
         "name": "server-name",  // The name of the server
         "host": "server-host",  // The hostname of the server
         "port": 50051 // The port that the server is listening on
      },
      "tls": { // The tls configuration for the client
         "enabled": false // A flag to indicate if TLS is enabled
      }
   }
]
```

For how the key is generated see the [Authentication Configuration](authentication.md) documentation.

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
