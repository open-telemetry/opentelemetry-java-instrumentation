# Library Instrumentation for SOFARPC version 5.4 and higher

Provides OpenTelemetry instrumentation for [SOFARPC](https://github.com/sofastack/sofa-rpc).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-sofa-rpc-5.4).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-sofa-rpc-5.4</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-sofa-rpc-5.4:OPENTELEMETRY_VERSION")
```

### Usage

Create filters using your configured `OpenTelemetry` instance:

```java
import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcTelemetry;

SofaRpcTelemetry telemetry = SofaRpcTelemetry.create(openTelemetry);
Filter clientFilter = telemetry.newClientFilter();
Filter serverFilter = telemetry.newServerFilter();
```

Add `clientFilter` to the filters passed to `ConsumerConfig.setFilterRef(...)` and
`serverFilter` to those passed to `ProviderConfig.setFilterRef(...)`, retaining any existing
application filters. Configure the filters before calling `ConsumerConfig.refer()` or `ProviderConfig.export()`.
For automatic filter registration, use the [autoconfigure module](../library-autoconfigure/README.md).
