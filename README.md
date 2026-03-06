# Distributed Unique ID Generator

A DUIG - distributed unique ID generator built with Spring Boot.

## ID Format (64 bits)

```
| 1 bit (sign=0) | 41 bits (timestamp) | 5 bits (datacenter) | 5 bits (machine) | 12 bits (sequence) |
```

- **Epoch**: 1288834974657 (DUIG default)
- **Max datacenters**: 32
- **Max machines per datacenter**: 32
- **Max sequence per millisecond**: 4096

## Prerequisites

- Java 21
- Docker & Docker Compose (for multi-instance setup)

## Build & Test

```bash
./gradlew build
./gradlew test
```

## Run Locally

```bash
# Default (datacenter=0, machine=0, port=8080)
./gradlew bootRun

# Custom configuration
DUIG_DATACENTER_ID=1 DUIG_MACHINE_ID=1 ./gradlew bootRun
```

## API Endpoints

### Generate Single ID

```bash
curl http://localhost:8080/api/id
```

Response:
```json
{
  "id": 1234567890123456789,
  "idHex": "112210f47de98115",
  "timestamp": 1709471234567,
  "datacenterId": 1,
  "machineId": 1,
  "sequence": 0
}
```

### Generate Bulk IDs

```bash
curl "http://localhost:8080/api/id/bulk?count=5"
```

Returns an array of ID responses. Default count is 10, max is 1000.

## Docker Compose

Run 3 generator instances:

```bash
docker compose up -d
```

| Service | Datacenter | Machine | Port |
|---------|:---:|:---:|:---:|
| generator-1 | 1 | 1 | 8081 |
| generator-2 | 1 | 2 | 8082 |
| generator-3 | 2 | 1 | 8083 |

### Run Test Client

Validates uniqueness across all 3 instances:

```bash
docker compose --profile test up test-client
```

## Architecture

Each generator instance is configured with a unique `(datacenterId, machineId)` pair. The DUIG algorithm guarantees global uniqueness without coordination between instances:

- Different instances produce different IDs because their datacenter/machine bits differ
- Within a single instance, the sequence counter ensures uniqueness within the same millisecond
- The `synchronized` keyword on `nextId()` ensures thread safety
