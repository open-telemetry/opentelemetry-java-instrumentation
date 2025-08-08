# Kafka Connect SinkTask Integration Test

This module contains integration tests for Kafka Connect SinkTask instrumentation using Testcontainers.

## Test Overview

The `KafkaConnectSinkTaskTest` class demonstrates how to test Kafka Connect SinkTask functionality with:

- **Kafka Container**: Apache Kafka using Confluent Platform
- **PostgreSQL Container**: PostgreSQL database as the sink destination
- **Kafka Connect Container**: Confluent Kafka Connect with JDBC connector

## Test Structure

### Test Setup
1. **Kafka Container**: Starts a Kafka broker for message streaming
2. **PostgreSQL Container**: Starts a PostgreSQL database for data storage
3. **Kafka Connect Container**: Starts Kafka Connect with the Confluent JDBC connector pre-installed
4. **Network Configuration**: All containers are connected via a Docker network for inter-container communication

### Test Scenarios

#### 1. Basic SinkTask Test (`testKafkaConnectSinkTask`)
- Sends JSON messages to a Kafka topic
- Verifies that Kafka Connect processes the messages and inserts them into PostgreSQL
- Validates both the count and content of inserted records

#### 2. Batch Processing Test (`testKafkaConnectSinkTaskWithMultipleBatches`)
- Sends multiple batches of messages to test bulk processing
- Verifies that all messages are correctly processed and stored

### Data Flow
```
JSON Messages → Kafka Topic → Kafka Connect JDBC Sink → PostgreSQL Table
```

## Running the Tests

### Prerequisites
- Docker installed and running
- Java 11 or higher
- Gradle

### Execute Tests
```bash
# Run all tests in the module
./gradlew :instrumentation:kafka:kafka-connect-2.6:javaagent:test

# Run only the SinkTask test
./gradlew :instrumentation:kafka:kafka-connect-2.6:javaagent:test --tests "KafkaConnectSinkTaskTest"
```

### Test Configuration

The test uses the following configuration:
- **Kafka Topic**: `test-topic`
- **PostgreSQL Database**: `testdb`
- **Table Name**: `test_table`
- **Connector**: Confluent JDBC Sink Connector (version 10.7.4)

### Test Data Format

Messages are sent as JSON strings with the following structure:
```json
{
  "name": "John Doe",
  "value": 100
}
```

These are automatically mapped to PostgreSQL table columns:
- `id` (SERIAL PRIMARY KEY)
- `name` (VARCHAR(255))
- `value` (INTEGER)
- `created_at` (TIMESTAMP)

## Key Features

1. **Full Integration**: Tests the complete data pipeline from Kafka to PostgreSQL
2. **Container Orchestration**: Uses Docker networks for seamless container communication
3. **Realistic Environment**: Uses actual Confluent components, not mocks
4. **Data Validation**: Verifies both data count and content accuracy
5. **Error Handling**: Includes proper cleanup and error handling
6. **Batch Processing**: Tests both single messages and batch processing scenarios

## Dependencies

The test relies on the following key dependencies:
- `org.testcontainers:kafka` - Kafka container support
- `org.testcontainers:postgresql` - PostgreSQL container support
- `org.postgresql:postgresql` - PostgreSQL JDBC driver
- `com.fasterxml.jackson.core:jackson-databind` - JSON processing
- `org.awaitility:awaitility` - Asynchronous testing utilities

## Notes

- The test automatically installs the Confluent JDBC connector during container startup
- All containers are started with appropriate logging for debugging
- The test includes proper timeouts and retry logic for container readiness
- Network isolation ensures tests don't interfere with each other
- The test focuses on data flow verification rather than OpenTelemetry spans (as requested)
