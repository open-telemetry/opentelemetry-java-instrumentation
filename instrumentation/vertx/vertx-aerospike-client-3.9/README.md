# Vert.x Aerospike Client Instrumentation

This module provides OpenTelemetry instrumentation for Aerospike database operations in Vert.x applications.

## Overview

This instrumentation automatically creates spans for Aerospike database operations (GET, PUT, DELETE, etc.) with relevant attributes following OpenTelemetry semantic conventions for database clients.

## Status

⚠️ **This is a template/starter module** - It requires customization based on your actual Aerospike client implementation with Vert.x.

## Setup Required

### 1. Update Dependencies

In `build.gradle.kts`, update the Aerospike client dependency to match your actual library:

```kotlin
library("io.vertx:vertx-aerospike-client:3.9.0")  // If such library exists
// OR
library("com.aerospike:aerospike-client:5.0.0")  // Standard Aerospike client
```

### 2. Customize Type Matcher

In `AerospikeClientInstrumentation.java`, update the `typeMatcher()` to match your actual client class:

```java
@Override
public ElementMatcher<TypeDescription> typeMatcher() {
  // Replace with actual class name
  return named("your.actual.aerospike.Client");
}
```

### 3. Adjust Method Matchers

Update the method matchers to match the actual API methods you want to instrument:

```java
transformer.applyAdviceToMethod(
    isMethod()
        .and(named("get"))  // Match your actual method names
        .and(takesArgument(0, ...)),  // Match actual parameter types
    ...
);
```

### 4. Extract Request Metadata

In `AerospikeClientInstrumentation.createRequest()`, implement actual metadata extraction:

```java
private static AerospikeRequest createRequest(String operation, Object key) {
  // Extract namespace, set, host, port from actual Aerospike Key/Client
  if (key instanceof com.aerospike.client.Key) {
    com.aerospike.client.Key aerospikeKey = (com.aerospike.client.Key) key;
    String namespace = aerospikeKey.namespace;
    String setName = aerospikeKey.setName;
    // ... extract other fields
  }
  
  return new AerospikeRequest(operation, namespace, setName, host, port);
}
```

### 5. Handle Async Operations

If your Aerospike client uses async operations (like Vert.x Future/Promise), you'll need to:

1. Create a handler wrapper (similar to `VertxRedisClientUtil.java` in Redis module)
2. Capture the context at operation start
3. End the span when the Future/Promise completes

Example:
```java
// In onEnter: wrap the callback handler
if (handler != null) {
  handler = wrapHandler(handler, request, context, parentContext);
}
```

### 6. Implement Tests

Update `VertxAerospikeClientTest.java`:

1. Add Aerospike Testcontainer setup
2. Create actual Aerospike client instance
3. Perform operations and verify spans
4. Remove `@Disabled` annotation

## Building

```bash
# Compile the module
./gradlew :instrumentation:vertx:vertx-aerospike-client-3.9:javaagent:compileJava

# Run tests (after implementing)
./gradlew :instrumentation:vertx:vertx-aerospike-client-3.9:javaagent:test

# Build the full agent with this instrumentation
./gradlew :javaagent:shadowJar
```

## Debugging

### Enable Debug Logging

Add to your advice code:

```java
System.out.println("[AEROSPIKE-DEBUG] Operation: " + operation + 
    ", TraceId: " + Span.current().getSpanContext().getTraceId());
```

### Run with Debug Agent

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.javaagent.debug=true \
     -Dotel.traces.exporter=logging \
     -jar your-app.jar
```

### Check Bytecode Transformation

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dnet.bytebuddy.dump=/tmp/bytebuddy-dump \
     -jar your-app.jar
```

Then inspect `/tmp/bytebuddy-dump/` for transformed classes.

## Module Structure

```
vertx-aerospike-client-3.9/
├── metadata.yaml                                    # Module description
├── README.md                                        # This file
└── javaagent/
    ├── build.gradle.kts                            # Build configuration
    └── src/
        ├── main/java/.../aerospike/
        │   ├── VertxAerospikeClientInstrumentationModule.java  # Entry point
        │   ├── AerospikeClientInstrumentation.java             # Bytecode advice
        │   ├── AerospikeRequest.java                           # Request model
        │   ├── AerospikeAttributesGetter.java                  # DB attributes
        │   ├── AerospikeNetAttributesGetter.java               # Network attributes
        │   └── AerospikeSingletons.java                        # Instrumenter setup
        └── test/java/.../aerospike/
            └── VertxAerospikeClientTest.java        # Tests (TODO: implement)
```

## Span Attributes

The instrumentation adds the following attributes to spans:

- `db.system`: "aerospike"
- `db.operation.name`: Operation name (GET, PUT, DELETE, etc.)
- `db.query.text`: Composed query text (e.g., "GET namespace.set")
- `db.namespace`: Aerospike namespace
- `db.collection.name`: Aerospike set name
- `server.address`: Server hostname
- `server.port`: Server port
- `network.peer.address`: Peer IP address
- `network.peer.port`: Peer port

## References

- [OpenTelemetry Java Instrumentation Docs](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [Writing Instrumentation Module Guide](../../docs/contributing/writing-instrumentation-module.md)
- [Vert.x Redis Client Instrumentation](../vertx-redis-client-3.9/) (reference implementation)
- [Aerospike Java Client](https://github.com/aerospike/aerospike-client-java)

## Next Steps

1. ✅ Basic module structure created
2. ⚠️ Update dependencies to match actual Aerospike client library
3. ⚠️ Customize type and method matchers for your API
4. ⚠️ Implement metadata extraction from Key/Client objects
5. ⚠️ Handle async operations if needed
6. ⚠️ Implement and enable tests
7. ⚠️ Test with real application
8. ⚠️ Add VirtualField for connection info if needed

