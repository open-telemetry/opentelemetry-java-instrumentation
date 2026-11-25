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

The library provides client and server `Filter` instances. Register each as a Dubbo filter extension
using an application-defined wrapper. For example, a consumer wrapper can delegate to the filter
created with your `OpenTelemetry` instance:

```java
public final class ClientFilterExtension implements Filter {
  public static volatile Filter delegate;

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
```

Initialize the wrapper and register it with Dubbo before creating consumers:

```java
ClientFilterExtension.delegate = DubboTelemetry.create(openTelemetry).newClientFilter();
ExtensionLoader<Filter> filterExtensions = ExtensionLoader.getExtensionLoader(Filter.class);
filterExtensions.addExtension("otelClient", ClientFilterExtension.class);

referenceConfig.setFilter("otelClient");
```

For providers, use a corresponding wrapper that delegates to
`DubboTelemetry.create(openTelemetry).newServerFilter()`, register it with `ExtensionLoader`, and set
its extension name with `ServiceConfig.setFilter(...)`. The plain library artifact does not register
filters automatically.
