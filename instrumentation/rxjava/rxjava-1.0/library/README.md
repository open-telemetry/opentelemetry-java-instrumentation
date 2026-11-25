# Library Instrumentation for RxJava version 1.x

Provides OpenTelemetry instrumentation for [RxJava](https://github.com/ReactiveX/RxJava) observables.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rxjava-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rxjava-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-1.0:OPENTELEMETRY_VERSION")
```

### Usage

Wrap an existing RxJava `Observable` using `TracedOnSubscribe.perSubscription`:

```java
import io.opentelemetry.instrumentation.rxjava.v1_0.TracedOnSubscribe;
import rx.Observable;

Observable<T> traced = Observable.create(
    TracedOnSubscribe.perSubscription(original, instrumenter, requestFactory));
```

Here, `original` is your `Observable<T>`, `instrumenter` is an
`Instrumenter<REQUEST, ?>` configured for the operation, and `requestFactory` is a
`Supplier<REQUEST>` producing a request for each subscription. The wrapper starts a span
per subscription and ends it when the subscription completes, fails, or is cancelled.
