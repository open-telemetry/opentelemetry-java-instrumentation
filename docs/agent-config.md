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

## Common instrumentation configuration

See [common instrumentation configuration properties](config/common.md).

## Suppressing specific auto-instrumentation

See [suppressing specific auto-instrumentation](suppressing-instrumentation.md)
