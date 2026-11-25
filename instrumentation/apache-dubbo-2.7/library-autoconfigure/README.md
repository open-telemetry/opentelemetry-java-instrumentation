# Library Instrumentation for Apache Dubbo version 2.7 and higher

Provides OpenTelemetry instrumentation for [Apache Dubbo](https://github.com/apache/dubbo).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dubbo-2.7).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-dubbo-2.7</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dubbo-2.7:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before creating Dubbo consumers or exporting
services. Dubbo discovers and activates the tracing filters automatically.

#### Using a custom OpenTelemetry instance

To configure tracing explicitly, create filters with your `OpenTelemetry` instance:

```java
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import org.apache.dubbo.rpc.Filter;

DubboTelemetry telemetry = DubboTelemetry.create(openTelemetry);
Filter clientFilter = telemetry.newClientFilter();
Filter serverFilter = telemetry.newServerFilter();
```

Delegate to these filters from your own Dubbo `Filter` extensions. Register the extensions in
`META-INF/dubbo/org.apache.dubbo.rpc.Filter` and select them in the corresponding consumer or provider
filter configuration. Exclude the automatic filter on each side you configure with
`-opentelemetryclient` or `-opentelemetryserver` to avoid tracing the same call twice.
