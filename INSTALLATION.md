# Installation

**Repository:** `federator`  
**Description:** `This file provides detailed installation steps, including required dependencies and configurations for the federator.`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

## Running the service with Docker

The [docker directory](docker) contains all the docker and application configuration required to run the various different
components that make up the federator service.  
For a detailed guide to running the service within docker see the [docker readme](/docker/README.md).

## Running the service locally

Please see this [running locally](./docs/running-locally.md) document that runs through setting up Federation locally and running with basic test data.

## Configuration

The Federator is configured using property and json files.  The configuration is split between the server and client.

### Server (producer)

See [server configuration](docs/server-configuration.md) for more details.

### Client (consumer)

See [client configuration](docs/client-configuration.md) for more details.

### Authentication Configuration

See [authentication configuration](docs/authentication.md) for more details.

### Logging configuration

See [logging configuration](docs/logging-configuration.md) for more details.

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
