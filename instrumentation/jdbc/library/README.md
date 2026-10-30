# Library Instrumentation for JDBC

Provides OpenTelemetry instrumentation for
[Java JDBC API](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-jdbc).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jdbc</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:OPENTELEMETRY_VERSION")
```

### Usage

There are two possible ways to activate the OpenTelemetry JDBC instrumentation. Instrumenting a `DataSource` is
preferable for DI frameworks which use connection pools. The other way requires changing the connection URL and using
a special OpenTelemetry driver.

#### Datasource way

If your application uses a `DataSource`, instrument it with `JdbcTelemetry`. This works well with dependency injection
(DI) frameworks such as
[Spring Framework](https://spring.io/projects/spring-framework), [Micronaut](https://micronaut.io),
[Quarkus](https://quarkus.io), or [Guice](https://github.com/google/guice).

```java
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.context.annotation.Configuration;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;

@Configuration
public class DataSourceConfig {

  @Bean
  public DataSource dataSource() {
    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl("jdbc:postgresql://127.0.0.1:5432/example");
    dataSource.setUsername("postgres");
    dataSource.setPassword("root");
    return JdbcTelemetry.create(openTelemetry).instrument(dataSource);
  }

}
```

#### Driver way

1. Activate tracing for JDBC connections by setting `jdbc:otel:` prefix to the JDBC URL, e.g. `jdbc:otel:h2:mem:test`.

2. Set the driver class to `io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver`.

3. Inject `OpenTelemetry` into `io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver` _before the initialization of the database connection pool_.
   You can do this with the `void setOpenTelemetry(OpenTelemetry openTelemetry)` method of `io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver`.
   Another way is to use `OpenTelemetryDriver.install(OpenTelemetry openTelemetry)`.
