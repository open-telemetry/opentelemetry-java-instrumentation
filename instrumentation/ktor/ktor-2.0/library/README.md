# Library Instrumentation for Ktor version 2.x

This package contains libraries to help instrument Ktor.
Server and client instrumentations are supported.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-ktor-2.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-ktor-2.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:OPENTELEMETRY_VERSION")
```

## Usage

## Initializing server instrumentation

Initialize instrumentation by installing the `KtorServerTelemetry` feature.
You must set the `OpenTelemetry` to use with the feature.

```kotlin
val openTelemetry: OpenTelemetry = ...

embeddedServer(Netty, 8080) {
  install(KtorServerTelemetry) {
    setOpenTelemetry(openTelemetry)
  }
}
```

## Initializing client instrumentation

Initialize instrumentation by installing the `KtorClientTelemetry` feature. You must set the
`OpenTelemetry` to use with
the feature.

```kotlin
val openTelemetry: OpenTelemetry = ...

val client = HttpClient {
  install(KtorClientTelemetry) {
    setOpenTelemetry(openTelemetry)
  }
}
```
