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
* [connection-configuration1-local.json](multiple-clients-connection-configuration1.json) - a connection configuration for a client that communicates with a single server
* [/multiple-clients-client2.propertie](multiple-clients-client2.properties) - a second client configuration
* [connection-configuration2-local.json](multiple-clients-connection-configuration2.json) - a connection configuration for a second client that communicates with a single server
* [multiple-clients-server.properties](multiple-clients-server.properties) - a server configuration that points to the absolute path for access.json that is within the docker configuration directory
* [multiple-clients-access.json](mutiple-clients-access.json) - a server access configuration file

### Amending a client password and hash code value

* A clients password is held within the connection-configuration configuration file within the "key" property
* Within the access.json the related hash value that is derived from this password (and a salt value) is stored in the hashed_key property
* In order to generate a hash code value, given password and salt string, a jshell script called [createClientHashValueJshell.java](./jshell/createClientHashValueJshell.java) has been provided.

