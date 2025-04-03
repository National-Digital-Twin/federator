## Examples

This directory gives example configurations that can be used to run from your IDE of choice.

A clients property location can be specified in a run configuration by setting the `FEDERATOR_CLIENT_PROPERTIES`.  
A servers property location can be specified in a run configuration by setting the `FEDERATOR_SERVER_PROPERTIES`.

### Example configuration files

#### Client Properties

* [client.properties](client.properties) - a client configuration to set up to work with [docker-compose-grpc-no-client.yml](../../docker/docker-compose-grpc-no-client.yml)
* [multi-server-connection-configuration.json](multi-server-connection-configuration.json) - the same client configuration as above but set up to work with 2 servers as described in [docker-compose-grpc-single-client-multi-server.yml](../../docker/docker-compose-grpc-single-client-multi-server.yml)

##### Connection configurations

* [connection-configuration.json](connection-configuration.json) - a connection configuration for a client that communicates with a single server, it is used by [client.properties](client.properties)
* [multi-server-connection-configuration](multi-server-connection-configuration.json) - a connection configuration for a client which 2 servers, used by [multi-server-connection-configuration.json](multi-server-connection-configuration.json)

