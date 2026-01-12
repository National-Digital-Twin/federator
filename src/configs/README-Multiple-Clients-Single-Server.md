## Examples

This directory gives example configuration files that can be used to run the multiple clients with single server configuration from your IDE of choice.

A clients property location can be specified in a run configuration by setting the`FEDERATOR_CLIENT_PROPERTIES`.  
* Each client can be run with a separate run configuration:
* Client one run configuration `FEDERATOR_CLIENT_PROPERTIES=$PROJECT_DIR$/src/configs/multiple-clients-client1.properties`   
* Client two run configuration `FEDERATOR_CLIENT_PROPERTIES=$PROJECT_DIR$/src/configs/multiple-clients-client2.properties`

A servers property location can be specified in a run configuration by setting the`FEDERATOR_SERVER_PROPERTIES`.
* A single server can be ran within a run configuration:
* `FEDERATOR_SERVER_PROPERTIES=$PROJECT_DIR$/src/configs/multiple-clients-server.properties`

### Configuration Files

* [multiple-clients-client1.properties](multiple-clients-client1.properties) - a client configuration
* [/multiple-clients-client2.propertie](multiple-clients-client2.properties) - a second client configuration
* [multiple-clients-server.properties](multiple-clients-server.properties) - a server configuration

