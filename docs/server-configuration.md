# Server Configuration

**Repository:** `federator`  
**Description:** `server configuration`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

## Provides the server configuration properties including the connection and custom filter configuration.

## Properties

Defined in a `server.properties` file whose location is defined by the `FEDERATOR_SERVER_PROPERTIES` environment variable:

|               Property                |                                                                                             Description                                                                                             |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kafka.boostrapServers`               | the bootstrap source server for the source Kafka instance                                                                                                                                           |
| `kafka.defaultKeyDeserializerClass`   | the default key deserializer class                                                                                                                                                                  |
| `kafka.defaultValueDeserializerClass` | the default value deserializer class                                                                                                                                                                |
| `kafka.consumerGroup`                 | the consumer group for the server                                                                                                                                                                   |
| `kafka.pollDuration`                  | the duration to poll for messages                                                                                                                                                                   |
| `kafka.pollRecords`                   | the number of records to poll for                                                                                                                                                                   |
| `kafka.additional.*`                  | a repeatable optional property to set any additional kafka consumer properties. These properties names must match with what kafka expects after the prefix has been removed                         |
| `shared.headers`                      | the headers definition for the Kafka messages                                                                                                                                                       |
| `filter.shareAll`                     | a flag to indicate if all messages should be shared                                                                                                                                                 |
| `server.port`                         | the port the server will listen on                                                                                                                                                                  |
| `server.tlsEnabled`                   | a flag to indicate if TLS is enabled                                                                                                                                                                |
| `server.keepAliveTime`                | the keep alive time for the server                                                                                                                                                                  |



For how the hashed key is generated see the [Authentication Configuration](authentication.md) documentation.

## Configuring a Custom Filter

Custom filters can be created to make access decisions on a `uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent<?,?>`.

- To enable a custom filter implement the [MessageFilter](../src/main/java/uk/gov/dbt/ndtp/federator/filter/MessageFilter.java) interface.

- A class that implements this interface is to provide a constructor that takes a single `String` that contains the client ID of the federation service.
  For example:

```java
 public KafkaEventHeaderAttributeAccessFilterNationality(String clientId) {
  this.clientId = clientId;
  updateAttributes();
}
```

- The `filterOut` method is to be implemented to return a boolean value that indicates if the message should be filtered out or not.
  For example:

```java
public boolean filterOut(KafkaEvent<?, ?> message) throws LabelException {
 // <filter logic>
 return true;
}
```


**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
