# Library Instrumentation for Quartz version 2.0 and higher

Provides OpenTelemetry instrumentation for [Quartz Scheduler](https://www.quartz-scheduler.org/),
enabling job execution spans.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-quartz-2.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-quartz-2.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-quartz-2.0:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.quartz.v2_0.QuartzTelemetry;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

QuartzTelemetry quartzTelemetry = QuartzTelemetry.create(openTelemetry);

Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
quartzTelemetry.configure(scheduler);

scheduler.start();
// Schedule your jobs - they will now be traced with OpenTelemetry
```
