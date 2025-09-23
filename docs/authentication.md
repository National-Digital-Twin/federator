# Authentication Configuration

**Repository:** `federator`  
**Description:** `authentication configuration`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

# Outlines the Federator server and client authentication configuration process.

A client and server authenticate with each other by using cryptographic hash (ie a one way function) value to provide proof of authentication (ie to prove they are who they claim to be).


A client is configured with a password property (in [connection-configuration.json](client-configuration.md#connection-configuration-json)).


The process of authentication follows the following use case:
1. When a client starts, it establishes a network connection to a server, providing its client password from the key property within the connection-configuration.json file.
2. The server calculates the hash value by taking the password it received and adding the salt value to the end.
3. The server now compares the calculated hash value with its stored value, if the values match then the client is authenticated.


Once these values have been generated then:
- The generated hash value is to be placed in the servers configuration file (access.json) within the `hashed_key` field
- The salt value is to be placed in the servers configuration file (access.json) within the `salt` field
- The password is to be placed in the clients configuration file (connection-configuration.json) within the `key` field

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
