# Library Instrumentation for AWS Lambda Core version 1.0 and higher

Provides OpenTelemetry instrumentation for [AWS Lambda Java handlers](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest instrumentation BOM release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-instrumentation-bom-alpha).
The BOM keeps the instrumentation and SDK dependencies on compatible versions.

The wrappers use SDK autoconfiguration and default to exporting traces, metrics, and logs over OTLP.
If you configure other exporters, include their dependencies. Omit the OTLP exporter only if no signal uses it.

For Maven, add to your `pom.xml`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
      <version>OPENTELEMETRY_VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-lambda-core-1.0</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:OPENTELEMETRY_VERSION"))
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-lambda-core-1.0")
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

### Usage

#### Using wrappers

To use the instrumentation, configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER` env property to your lambda handler method in following format `package.ClassName::methodName`
and use one of wrappers as your lambda `Handler`.

In order to configure a span flush timeout (default is set to 10 seconds), please configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT` env property. The value is in milliseconds.

Available wrappers:

- `io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestStreamWrapper` - for wrapping streaming handlers (implementing `RequestStreamHandler`), enabling HTTP context propagation for HTTP requests

When using known Lambda event types as parameters, use [aws-lambda-events-3.11](../../aws-lambda-events-3.11/library).

#### Using handlers

To use the instrumentation, replace your function classes that implement `RequestHandler` (or `RequestStreamHandler`) with those
that extend `TracingRequestHandler` (or `TracingRequestStreamHandler`). You will need to change the method name to `doHandleRequest`
and pass an initialized `OpenTelemetrySdk` to the base class.

```java
public class MyRequestHandler extends TracingRequestHandler<String, String> {

  private static final OpenTelemetrySdk SDK = OpenTelemetrySdk.builder()
      .addSpanProcessor(spanProcessor)
      .buildAndRegisterGlobal();

  public MyRequestHandler() {
    super(SDK);
  }

  // Note the method is named doHandleRequest instead of handleRequest.
  @Override
  protected String doHandleRequest(String input, Context context) {
    if (input.equals("hello")) {
      return "world";
    }
    return "goodbye";
  }
}
```

A `SERVER` span will be created with the name you specify for the function when deploying it.

In addition, it is recommended to set up X-Ray trace propagation to be able to
link to tracing information provided by Lambda itself. To do so, add a dependency on
`io.opentelemetry.contrib:opentelemetry-aws-xray-propagator`.

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.contrib/opentelemetry-aws-xray-propagator).

Gradle:

```kotlin
dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator:OPENTELEMETRY_VERSION")
}
```

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.contrib</groupId>
    <artifactId>opentelemetry-aws-xray-propagator</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

## Trace propagation

Context propagation for this instrumentation can be done either with X-Ray propagation or regular HTTP propagation. If X-Ray is enabled for instrumented lambda, it will be preferred. If X-Ray is disabled, HTTP propagation will be tried (that is HTTP headers will be read to check for a valid trace context).

### X-Ray propagation

This instrumentation supports propagating traces using the `X-Amzn-Trace-Id` format for both normal
requests and SQS requests. X-Ray propagation is always enabled, there is no need to configure it explicitly.

### HTTP headers based propagation

For API Gateway (HTTP) requests instrumented by using one of following methods:

- extending `TracingRequestStreamHandler` or `TracingRequestHandler`
- wrapping with `TracingRequestStreamWrapper` or `TracingRequestApiGatewayWrapper`
  traces can be propagated with supported HTTP headers (see <https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/trace-propagators>).

In order to enable requested propagation for a handler, configure it on the SDK you build.

```java
  static {
    OpenTelemetrySdk.builder()
      ...
      .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
      .buildAndRegisterGlobal();
  }
```

If using the wrappers, set the `OTEL_PROPAGATORS` environment variable as described [here](https://opentelemetry.io/docs/languages/sdk-configuration/general/#otel_propagators).
