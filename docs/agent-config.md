# Agent Configuration

## NOTE: subject to change!

Note: The environment variables/system properties in this document are very likely to change over time.
Please check back here when trying out a new version!

Please report any bugs or unexpected behavior you find.

## Contents

* [SDK Autoconfiguration](#sdk-autoconfiguration)
* [Configuring the agent](#configuring-the-agent)
* [Peer service name](#peer-service-name)
* [DB statement sanitization](#db-statement-sanitization)
* [Suppressing specific auto-instrumentation](#suppressing-specific-auto-instrumentation)

## SDK Autoconfiguration

The SDK's autoconfiguration module is used for basic configuration of the agent. Read the
[docs](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
to find settings such as configuring export or sampling.

Here are some quick links into those docs for the configuration options for specific portions of the SDK & agent:

* [Exporters](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#exporters)
  + [OTLP exporter (both span and metric exporters)](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#otlp-exporter-both-span-and-metric-exporters)
  + [Jaeger exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#jaeger-exporter)
  + [Zipkin exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#zipkin-exporter)
  + [Prometheus exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#prometheus-exporter)
  + [Logging exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#logging-exporter)
* [Trace context propagation](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#propagator)
* [OpenTelemetry Resource and service name](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#opentelemetry-resource)
* [Batch span processor](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#batch-span-processor)
* [Sampler](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#sampler)
* [Span limits](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#span-limits)
* [Using SPI to further configure the SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/README.md#customizing-the-opentelemetry-sdk)

## Configuring the agent

The agent can consume configuration from one or more of the following sources (ordered from highest to lowest priority):
* system properties
* environment variables
* the [configuration file](#configuration-file)
* the [`ConfigPropertySource`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/javaagent-extension-api/src/main/java/io/opentelemetry/javaagent/extension/config/ConfigPropertySource.java) SPI

### Configuration file

You can provide a path to agent configuration file by setting the corresponding property.

| System property                      | Environment variable                 | Description                                                                      |
|--------------------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `otel.javaagent.configuration-file` | `OTEL_JAVAAGENT_CONFIGURATION_FILE` | Path to valid Java properties file which contains the javaagent configuration.|

### Extensions

You can enable [extensions](../examples/extension/README.md) by setting the corresponding property.

| System property                      | Environment variable                 | Description                                                                      |
|--------------------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `otel.javaagent.extensions` | `OTEL_JAVAAGENT_EXTENSIONS` | Path to a an extension jar file or folder, containing jar files. If pointing to a folder, every jar file in that folder will be treated as separate, independent extension|

## Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes) is the name of a remote service being connected to. It corresponds to `service.name` in the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service) for the local service.

| System property                      | Environment variable                 | Description                                                                      |
|--------------------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `otel.instrumentation.common.peer-service-mapping` | `OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING` | Used to specify a mapping from hostnames or IP addresses to peer services, as a comma-separated list of host=name pairs. The peer service is added as an attribute to a span whose host or IP match the mapping. For example, if set to 1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have an attribute of `dogs-api`. |

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
| `otel.instrumentation.common.db-statement-sanitizer.enabled` | `OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED` | Enables the DB statement sanitization. The default value is `true`. |

## Suppressing specific auto-instrumentation

See [suppressing specific auto-instrumentation](suppressing-instrumentation.md)
