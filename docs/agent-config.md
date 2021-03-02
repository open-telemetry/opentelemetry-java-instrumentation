# Configuration parameters

## NOTE: subject to change!

Note: These parameter names are very likely to change over time, so please check
back here when trying out a new version! Please report any bugs or unexpected
behavior you find.

## Contents

* [SDK Autoconfiguration](#sdk-autoconfiguration)
* [Peer service name](#peer-service-name)
* [DB statement sanitization](#db-statement-sanitization)
* [Customizing the OpenTelemetry SDK](#customizing-the-opentelemetry-sdk)
* [Suppressing specific auto-instrumentation](#suppressing-specific-auto-instrumentation)

## SDK Autoconfiguration

The SDK's autoconfiguration module is used for basic configuration of the agent. Read the
[docs](https://github.com/open-telemetry/opentelemetry-java/tree/v1.0.0/sdk-extensions/autoconfigure#customizing-the-opentelemetry-sdk)
to find settings such as configuring export or sampling.

## Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes) is the name of a remote service being connected to. It corresponds to `service.name` in the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service) for the local service.

| System property                      | Environment variable                 | Description                                                                      |
|--------------------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `otel.endpoint.peer.service.mapping` | `OTEL_ENDPOINT_PEER_SERVICE_MAPPING` | Used to specify a mapping from hostnames or IP addresses to peer services, as a comma-separated list of host=name pairs. The peer service is added as an attribute to a span whose host or IP match the mapping. For example, if set to 1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have an attribute of `dogs-api`. |

## DB statement sanitization

The agent sanitizes all database queries/statements before setting the `db.statement` semantic attribute:
all values (strings, numbers) in the query string are replaced with a question mark `?`.

Examples:
* SQL query `SELECT a from b where password="secret"` will appear as `SELECT a from b where password=?` in the exported span;
* Redis command `HSET map password "secret"` will appear as `HSET map password ?` in the exported span.

This behavior is turned on by default for all database instrumentations.
The following property may be used to disable it:

| System property                                       | Environment variable                                  | Description                                                         |
|-------------------------------------------------------|-------------------------------------------------------|---------------------------------------------------------------------|
| `otel.instrumentation.db-statement-sanitizer.enabled` | `OTEL_INSTRUMENTATION_DB_STATEMENT_SANITIZER_ENABLED` | Enables the DB statement sanitization. The default value is `true`. |

## Customizing the OpenTelemetry SDK

*Customizing the SDK is highly advanced behavior and is still in the prototyping phase. It may change drastically or be removed completely. Use
with caution*

The OpenTelemetry SDK exposes SPI [hooks](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/spi)
for customizing its behavior, such as the `Resource` attached to spans or the `Sampler`.

Because the automatic instrumentation runs in a different classpath than the instrumented application, it is not possible for customization in the application to take advantage of this customization. In order to provide such customization, you can provide the path to a JAR file, including an SPI implementation using the system property `otel.initializer.jar`. Note that this JAR needs to shade the OpenTelemetry API in the same way as the agent does. The simplest way to do this is to use the same shading configuration as the agent from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfade733b899a2f02cfec7033c6a1efd7c54fd8b/java-agent/java-agent.gradle#L39). In addition, you must specify the `io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.spi.TraceProvider` to the name of the class that implements the SPI.

## Suppressing specific auto-instrumentation

See [suppressing specific auto-instrumentation](suppressing-instrumentation.md)
