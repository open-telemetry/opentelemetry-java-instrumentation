# Library Instrumentation for Restlet version 1.1 and higher

Provides OpenTelemetry instrumentation for [Restlet](https://restlet.talend.com/), enabling HTTP
server spans.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-restlet-1.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-restlet-1.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-restlet-1.1:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.restlet.v1_1.RestletTelemetry;
import org.restlet.Filter;
import org.restlet.Application;
import org.restlet.Restlet;

public class RestletExample {
  public static void main(String[] args) throws Exception {
    // Get an OpenTelemetry instance
    OpenTelemetry openTelemetry = ...;

    RestletTelemetry restletTelemetry = RestletTelemetry.create(openTelemetry);
    Filter tracingFilter = restletTelemetry.newFilter("/api");

    Application application = new Application() {
      @Override
      public Restlet createInboundRoot() {
        return tracingFilter;
      }
    };
  }
}
```
