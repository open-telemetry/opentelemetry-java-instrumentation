# Library Instrumentation for Apache Commons Pool version 2.0 and higher

Provides OpenTelemetry instrumentation for [Apache Commons Pool](https://commons.apache.org/proper/commons-pool/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the latest release.

For Maven, add to your `pom.xml` dependencies:

```xml
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-apache-commons-pool-2.0</artifactId>
  <version>OPENTELEMETRY_VERSION</version>
</dependency>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-commons-pool-2.0:OPENTELEMETRY_VERSION")
```

### Usage

Register supported `GenericObjectPool` or `GenericKeyedObjectPool` instances for metrics collection.
The pool name must be stable and unique.

```java
CommonsPoolTelemetry telemetry = CommonsPoolTelemetry.create(openTelemetry);

telemetry.registerMetrics(pool, "my-pool");

// When done:
telemetry.unregisterMetrics(pool);
```

## Metrics

These metrics are Apache Commons Pool specific and do not currently follow OpenTelemetry semantic conventions.

| Metric                                        | Description                                                                    | Unit        |
| --------------------------------------------- | ------------------------------------------------------------------------------ | ----------- |
| `apache.commons_pool.object.count`            | The number of objects currently in the state described by the state attribute. | `{object}`  |
| `apache.commons_pool.object.idle.min`         | The minimum number of idle objects allowed in the pool.                        | `{object}`  |
| `apache.commons_pool.object.idle.max`         | The maximum number of idle objects allowed in the pool.                        | `{object}`  |
| `apache.commons_pool.object.max`              | The maximum number of objects allowed in the pool.                             | `{object}`  |
| `apache.commons_pool.object.pending_requests` | The number of requests currently waiting for an object from the pool.          | `{request}` |
