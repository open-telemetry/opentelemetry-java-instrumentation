# Library Instrumentation for SOFARPC 5.4 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-sofa-rpc-5.4):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-sofa-rpc-5.4:OPENTELEMETRY_VERSION")
```

## Usage

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
application filters. Configure the filters before referring the client or exporting the
provider. For automatic filter registration, use the
[autoconfigure module](../../sofa-rpc-5.4/library-autoconfigure).
