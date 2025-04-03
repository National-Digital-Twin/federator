# Uninstall

**Repository:** `federator`  
**Description:** `This file provides detailed steps to remove this repository, including any dependencies and configurations for Federator.`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

Uninstalling and removing the repository involves 3 parts:
- Java installation and configuration
- Docker configuration and containers
- Delete repository clone

## Removing the Java installation

Run:

```
mvn clean
```

## Deleting Docker containers

1. List all containers to find the container ID or name:

```
docker ps -a
```

2. Stop the and remove the containers, using the docker compose down command for the required configuration:

For example for the gRPC solution run the following command, from the project root directory:

```shell
docker compose --file docker/docker-compose-grpc.yml down --volumes
```

4. Confirm Docker has been deleted by listing all Docker containers:

```
docker ps -a
```

## Deleting repository clone

Simply delete the cloned repository files from working location.

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
