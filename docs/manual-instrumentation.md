# Manual instrumentation

For most users, the out-of-the-box instrumentation is completely sufficient and nothing more has to
be done.  Sometimes, however, users wish to add attributes to the otherwise automatic spans,
or they might want to manually create spans for their own custom code.

## Contents

- [Manual instrumentation](#manual-instrumentation)
- [Dependencies](#dependencies)
  * [Maven](#maven)
  * [Gradle](#gradle)
- [Adding attributes to the current span](#adding-attributes-to-the-current-span)
- [Creating spans around methods with `@WithSpan`](#creating-spans-around-methods-with-withspan)
  * [Suppressing `@WithSpan` instrumentation](#suppressing-withspan-instrumentation)
  * [Creating spans around methods with `otel.instrumentation.methods.include`](#creating-spans-around-methods-with-otelinstrumentationmethodsinclude)
- [Creating spans manually with a Tracer](#creating-spans-manually-with-a-tracer)

# Dependencies

> :warning: prior to version 1.0.0, the Java agent
only supports manual instrumentation using the `opentelemetry-api` version with the same version
number as the Java agent you are using. Starting with 1.0.0, the Java agent will start supporting
multiple (1.0.0+) versions of `opentelemetry-api`.

You'll need to add a dependency on the `opentelemetry-api` library to get started; if you intend to
use the `@WithSpan` annotation, also include the `opentelemetry-extension-annotations` dependency.

## Maven

```xml
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-api</artifactId>
      <version>1.0.0</version>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-extension-annotations</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
```

## Gradle

```groovy
dependencies {
    implementation('io.opentelemetry:opentelemetry-api:1.0.0')
    implementation('io.opentelemetry:opentelemetry-extension-annotations:1.0.0')
}
```

# Adding attributes to the current span

A common need when instrumenting an application is to capture additional application-specific or
business-specific information as additional attributes to an existing span from the automatic
instrumentation. Grab the current span with `Span.current()` and use the `setAttribute()`
methods:

```java
import io.opentelemetry.api.trace.Span;

// ...

Span span = Span.current();
span.setAttribute(..., ...);
```

# Creating spans around methods with `@WithSpan`

Another common situation is to capture a span corresponding to one of your methods. The
`@WithSpan` annotation makes this straightforward:

```java
import io.opentelemetry.extension.annotations.WithSpan;

public class MyClass {
  @WithSpan
  public void MyLogic() {
      <...>
  }
}
```

Each time the application invokes the annotated method, it creates a span that denote its duration
and provides any thrown exceptions. Unless specified as an argument to the annotation, the span name
will be `<className>.<methodName>`.


## Adding attributes to the span with `@SpanAttribute`

When a span is created for an annotated method the values of the arguments to the method invocation
can be automatically added as attributes to the created span by annotating the method parameters
with the `@SpanAttribute` annotation.

```java
import io.opentelemetry.extension.annotations.SpanAttribute;
import io.opentelemetry.extension.annotations.WithSpan;

public class MyClass {
  @WithSpan
  public void MyLogic(@SpanAttribute("parameter1") String parameter1, @SpanAttribute("parameter2") long parameter2) {
      <...>
  }
}
```

Unless specified as an argument to the annotation, the attribute name will be derived from the
formal parameter names if they are compiled into the `.class` files by passing the `-parameters`
option to the `javac` compiler.

## Suppressing `@WithSpan` instrumentation

Suppressing `@WithSpan` is useful if you have code that is over-instrumented using `@WithSpan`
and you want to suppress some of them without modifying the code.

| System property                 | Environment variable            | Purpose                                                                                                                                  |
|---------------------------------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| otel.instrumentation.opentelemetry-annotations.exclude-methods | OTEL_INSTRUMENTATION_OPENTELEMETRY_ANNOTATIONS_EXCLUDE_METHODS | Suppress `@WithSpan` instrumentation for specific methods.
Format is "my.package.MyClass1[method1,method2];my.package.MyClass2[method3]" |

## Creating spans around methods with otel.instrumentation.methods.include
This is a way to to create a span around a first-party code method without using `@WithSpan`.

| System property                 | Environment variable            | Purpose                                                                                                                                  |
|---------------------------------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| otel.instrumentation.methods.include |                                 | Add instrumentation for specific methods in lieu of `@WithSpan`.
Format is "my.package.MyClass1[method1,method2];my.package.MyClass2[method3]" |

# Creating spans manually with a Tracer

If `@WithSpan` doesn't work for your specific use case, you're still in luck!

The underlying OpenTelemetry API allows you to [obtain a tracer](https://github.com/open-telemetry/opentelemetry-java/blob/main/QUICKSTART.md#tracing)
that can be used to [manually create spans](https://github.com/open-telemetry/opentelemetry-java/blob/main/QUICKSTART.md#create-a-basic-span)
and execute code within the scope of that span.

See the [OpenTelemetry Java
QuickStart](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md#tracing)
for a detailed en example of how to configure OpenTelemetry with code and
how to use the `Tracer`, `Scope` and `Span` interfaces to
instrument your application.
