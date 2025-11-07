# Library Instrumentation for Java Servlet version 3.0 and higher

Provides OpenTelemetry instrumentation for Java Servlets through a servlet filter.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with
the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-lettuce-5.1).

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

Add the filter to your `web.xml`

```xml
<web-app>
  <filter>
    <filter-name>OpenTelemetryServletFilter</filter-name>
    <filter-class>io.opentelemetry.instrumentation.servlet.v3_0.OpenTelemetryServletFilter
    </filter-class>
    <async-supported>true</async-supported>
  </filter>
  <filter-mapping>
    <filter-name>OpenTelemetryServletFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
</web-app>
```

Note: GlobalOpenTelemetry must be set before filter initialization. If you are unable to ensure it
is set first, consider creating a subclass of `OpenTelemetryServletFilter` that handles
initialization of GlobalOpenTelemetry in a static initializer or constructor.
