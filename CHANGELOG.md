# Changelog

**Repository:** `federator`  
**Description:** `Tracks all notable changes, version history, and roadmap toward 1.0.0 following Semantic Versioning.`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

--- 

All notable changes to this repository will be documented in this file.

This project follows **Semantic Versioning (SemVer)** ([semver.org](https://semver.org/)), using the format:

`[MAJOR].[MINOR].[PATCH]`
- **MAJOR** (`X.0.0`) – Incompatible API/feature changes that break backward compatibility.
- **MINOR** (`0.X.0`) – Backward-compatible new features, enhancements, or functionality changes.
- **PATCH** (`0.0.X`) – Backward-compatible bug fixes, security updates, or minor corrections.
- **Pre-release versions** – Use suffixes such as `-alpha`, `-beta`, `-rc.1` (e.g., `2.1.0-beta.1`).
- **Build metadata** – If needed, use `+build` (e.g., `2.1.0+20250314`).

---

## 1.1.0 - 2026-02-19

### Added

- File Stream between Consumer and producer
- AWS S3 bucket File Storage Integration on both Producer and Consumer
- Azure Blob Storage Integration on both Producer and Consumer
- Integration with Keycloak authentication system
- Support for Redis authentication
- Support for JWT access token caching using Redis
- Attribute filtering for file streaming
- Contract for file exchange
- Support for managed and workload identity service accounts for Azure
- Azure-to-AWS file streaming support
- Resilience4j retry and circuit breaker support
- GitHub Actions workflow for MkDocs deployment
- SBOM generation to GitHub release process
- Enhanced Redis, MTLS, and GRPC logging configurations
- Support for server-side consumer verification
- Client JWT audience validation functionality
- Producer-consumer configuration retrieval from Management-Node using JWT-TOKEN
- Support for toggling IDP MTLS Flag
- Documentation for deploying a federator locally, including smoke test docs for one-to-one configurations

### Fixed

- Fixed offset tracking for consumers
- Fixed unit tests in pipeline
- Fixed SonarQube issues (ThreadUtil, try-with-resources, assertions)
- Fixed failing smoke tests (multi-client, multi-server)
- Fixed upgraded AWS SDK dependency request template
- Handled invalid cloud storage configuration paths
- Added failure handling scenarios
- Closed and finished gRPC jobs when no further messages received
- Disconnected clients from server when no further messages available to stream

### Changed

- Upgraded Java version and integrated ManagementNodeDataHandler
- Restructured packages and refactored gRPC client
- Split Consumer and Producer logic
- Removed AccessMap and legacy connection/access configuration files
- Updated client_id to azp for compatibility
- Updated README.md and deduplicated documentation
- Improved job scheduling and configuration handling
- Removed get-topic functionality
- Update code coverage rules and CI workflow
- Optimized file streaming memory usage and increased default chunk size

### Dependencies

- Bumped com.google.protobuf:protobuf-java from 3.25.5 to 4.30.2
- Bumped org.projectlombok:lombok from 1.18.36 to 1.18.38
- Bumped dependency.io-grpc from 1.70.0 to 1.71.0
- Bumped org.jacoco:jacoco-maven-plugin from 0.8.12 to 0.8.13
- Bumped org.mockito:mockito-bom from 5.16.1 to 5.17.0
- Upgraded AWS SDK to version 2.202.x

---

## 1.0.0 - 2025-10-01

### Added

- Integration with Keycloak authentication system
- Introducing JobRunR to Consumer
- Support for Redis authentication
- Support for JWT access token caching using Redis
- SBOM generation to GitHub release process
- Enhanced Redis, MTLS and GRPC logging configurations
- Support for server-side consumer verification
- Client JWT audience validation functionality
- Filter functionality based on header
- Producer consumer configuration retrieval from Management
- Support for toggling IDP MTLS Flag

### Fixed

- Fixed offset tracking for consumers
- Fixed unit tests in pipeline
- Fixed Security label filtering
- Fixed failing smoke test in `docker/docker-compose-multiple-clients-multiple-server.yml`
- Fixed missing smoke-test in `docker/docker-compose-grpc.yml`
- Fixed SonarQube `try-with-resources or close ScheduledExecutorService in a finally clause` issue
- Closed and finished grpc jobs when no further messages received
- Disconnected clients from server when no further messages available to stream

### Changed

- Created documentation for deploying a federator locally
- Update architecture diagrams showing new features and Integrations
- Updated documentation for running locally and running on docker to deduplicate common instructions
- Updated documentation for smoke tests on one to one server/client configurations
- Updated client_id to azp for compatibility
- Split Consumer Producer logic
- Removed AccessMap
- Removed obsolete workflows
- Upgraded Java version and integrated ManagementNodeDataHandler
- Refactored offset handling logic in GRPCFileClient by introducing `saveNextOffsetToRedis` helper method to improve code clarity and maintainability
- Optimized file streaming memory usage by using `UnsafeByteOperations.unsafeWrap()` instead of `ByteString.copyFrom()` to avoid unnecessary byte array copies
- Increased default file stream chunk size from 1KB to 1MB (`file.stream.chunk.size` defaults to `1000000` bytes) to improve streaming performance and reduce memory overhead

### Dependencies

- Bumped com.google.protobuf:protobuf-java from 3.25.5 to 4.30.2
- Bumped org.projectlombok:lombok from 1.18.36 to 1.18.38
- Bumped dependency.io-grpc from 1.70.0 to 1.71.0
- Bumped org.jacoco:jacoco-maven-plugin from 0.8.12 to 0.8.13
- Bumped org.mockito:mockito-bom from 5.16.1 to 5.17.0

---

## 0.90.0 – 2025-04-04

### Initial Public Release (Pre-Stable)

This is the first public release of this repository under NDTP's open-source governance model.  
Since this release is **pre-1.0.0**, changes may still occur that are **not fully backward-compatible**.

#### Initial Features

- Key functionality for listening to a Kafka topic for new data, filtering it and sending to another federator for consumption in another IA Node.
- Key functionality for configuring the instance of a federator to control that activity.

#### Known Limitations

- Some components are subject to change before `1.0.0`.
- APIs may evolve based on partner feedback and internal testing.

---

## [0.90.1] – YYYY-MM-DD

### Fixed

- Security patch addressing [issue].
- Minor bug fix in [module].

---

## [0.91.0] – YYYY-MM-DD

### Added

- New feature: [Feature name].

### Changed

- Adjusted API contracts for [component].

---

## Future Roadmap to `1.0.0`

The `0.90.x` series is part of NDTP’s **pre-stable development cycle**, meaning:
- **Minor versions (`0.91.0`, `0.92.0`...) introduce features and improvements** leading to a stable `1.0.0`.
- **Patch versions (`0.90.1`, `0.90.2`...) contain only bug fixes and security updates**.
- **Backward compatibility is NOT guaranteed until `1.0.0`**, though NDTP aims to minimise breaking changes.

Once `1.0.0` is reached, future versions will follow **strict SemVer rules**.

---

## Versioning Policy

1. **MAJOR updates (`X.0.0`)** – Typically introduce breaking changes that require users to modify their code or configurations.

- **Breaking changes (default rule)**: Any backward-incompatible modifications require a major version bump.
- **Non-breaking major updates (exceptional cases)**: A major version may also be incremented if the update represents a significant milestone, such as a shift in governance, a long-term stability commitment, or substantial new functionality that redefines the project’s scope.

2. **MINOR updates (`0.X.0`)** – New functionality that is backward-compatible.
3. **PATCH updates (`0.0.X`)** – Bug fixes, performance improvements, or security patches.
4. **Dependency updates** – A **major dependency upgrade** that introduces breaking changes should trigger a **MAJOR** version bump (once at `1.0.0`).

---

## How to Update This Changelog

1. When making changes, update this file under the **Unreleased** section.
2. Before a new release, move changes from **Unreleased** to a new dated section with a version number.
3. Follow **Semantic Versioning** rules to categorise changes correctly.
4. If pre-release versions are used, clearly mark them as `-alpha`, `-beta`, or `-rc.X`.

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](OGL_LICENSE.md).
