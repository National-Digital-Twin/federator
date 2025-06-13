# One to One (Single Client with Single Server) Smoke Tests

## Use Case

**Given** I send the `simple-sample-test.dat` data file to producer Kafka

**When** that file contains **40** records of test data and the following filters are applied:

* Nationality: `GBR`
* Clearance: `O`
* Organisation Type: `NON-GOV`

**Then** I should see **23** records in the consumer IA Node data store, and those records should contain the same
attributes listed above (Nationality, Clearance, Organisation Type)

## Running the tests

To run the tests you'll need to spin up the following docker compose file:

```shells
docker compose --file docker/docker-compose-grpc.yml up -d   
```

## Changing the test parameters

### Changing the test data file

To change the test data file that is sent via kafka, you must modify the `KNOWLEDGE_DATA` environment variable in the
`docker/.env` the filename of your desired file, ensuring that the file is in the `docker/input/knowledge` directory.

```
KNOWLEDGE_DATA=simple-sample-test.dat  # Change this to your desired filename
```

### Changing the filters

Changing the data filters can be done by modifiying the `attributes` in `docker/docker-grpc-resources/access.json`

```json
"attributes": {
    "nationality": "GBR",
    "clearance": "O",
    "organisation_type": "NON-GOV"
}
```

## Viewing the test outcome

The test outcome can be viewed by checking the logs of kafka-message-counter, which will check the message count in the
Kafka topic against the expected number of messages.

```shell
docker logs kafka-message-counter --follow 
```

### Clean up

When finished with the smoke tests you can take down the containers to save resources:

```shell
docker compose --file docker/docker-compose-grpc.yml down
```

