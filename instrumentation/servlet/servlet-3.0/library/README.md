# Library Instrumentation for Java Servlet version 3.0 to 5.0

Provides OpenTelemetry instrumentation for Java Servlets through a servlet filter.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with
the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-servlet-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-servlet-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-servlet-3.0:OPENTELEMETRY_VERSION")
```

### Usage

Create telemetry producing servlet filter as shown below:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.servlet.v3_0.ServletTelemetry;
import javax.servlet.Filter;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create a ServletTelemetry instance
ServletTelemetry telemetry = ServletTelemetry.create(openTelemetry);

// Create telemetry producing servlet filter
Filter filter = telemetry.createFilter();
```

### Capture selected request parameters

```java
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.servlet.v3_0.ServletTelemetryBuilder;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Experimental;

ServletTelemetryBuilder builder = ServletTelemetry.builder(openTelemetry);
Experimental.setRequestParameters(
    builder,
    IncludeExclude.builder()
        .setIncluded("user-*", "search-?")
        .setExcluded("password", "*-token")
        .build());
Filter filter = builder.build().createFilter();
```

Matching is case-sensitive, and excluded patterns take precedence over included patterns. An absent or empty selector captures nothing; an exclude-only selector captures all available parameters except those excluded.
