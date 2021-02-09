# Configuration parameters

## NOTE: subject to change!

Note: These parameter names are very likely to change over time, so please check
back here when trying out a new version! Please report any bugs or unexpected
behavior you find.

## Contents

* [Exporters](#exporters)
  + [OTLP exporter (both span and metric exporters)](#otlp-exporter--both-span-and-metric-exporters-)
  + [Jaeger exporter](#jaeger-exporter)
  + [Zipkin exporter](#zipkin-exporter)
  + [Prometheus exporter](#prometheus-exporter)
  + [Logging exporter](#logging-exporter)
* [Trace context propagation](#propagator)
* [OpenTelemetry Resource](#opentelemetry-resource)
* [Peer service name](#peer-service-name)
* [Batch span processor](#batch-span-processor)
* [Trace config](#trace-config)
* [Interval metric reader](#interval-metric-reader)
* [Customizing the OpenTelemetry SDK](#customizing-the-opentelemetry-sdk)
* [Suppressing specific auto-instrumentation](#suppressing-specific-auto-instrumentation)


## Exporters

The following configuration properties are common to all exporters:

| System property | Environment variable | Purpose                                                                                                                                                 |
|-----------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| otel.trace.exporter   | OTEL_TRACE_EXPORTER        | The exporter to be used for tracing. Default is `otlp`. `none` means no exporter. |
| otel.metrics.exporter   | OTEL_METRICS_EXPORTER        | The exporter to be used for metrics. Default is `otlp`. `none` means no exporter. |

### OTLP exporter (both span and metric exporters)

A simple wrapper for the OpenTelemetry Protocol (OTLP) span and metric exporters of opentelemetry-java.

| System property              | Environment variable        | Description                                                               |
|------------------------------|-----------------------------|---------------------------------------------------------------------------|
| otel.trace.exporter=otlp (default) | OTEL_TRACE_EXPORTER=otlp          | Select the OpenTelemetry exporter for tracing (default)                                   |
| otel.metrics.exporter=otlp (default) | OTEL_METRICS_EXPORTER=otlp          | Select the OpenTelemetry exporter for metrics (default)                                   |
| otel.exporter.otlp.endpoint  | OTEL_EXPORTER_OTLP_ENDPOINT | The OTLP endpoint to connect to. Must be a URL with a scheme of either `http` or `https` based on the use of TLS. Default is `http://localhost:4317`.            |
| otel.exporter.otlp.headers   | OTEL_EXPORTER_OTLP_HEADERS  | Key-value pairs separated by semicolons to pass as request headers        |
| otel.exporter.otlp.timeout   | OTEL_EXPORTER_OTLP_TIMEOUT  | The maximum waiting time allowed to send each batch. Default is `1000`.   |

To configure the service name for the OTLP exporter, add the `service.name` key
to the OpenTelemetry Resource ([see below](#opentelemetry-resource)), e.g. `OTEL_RESOURCE_ATTRIBUTES=service.name=myservice`.

### Jaeger exporter

A simple wrapper for the Jaeger exporter of opentelemetry-java. This exporter uses gRPC for its communications protocol.

| System property                   | Environment variable              | Description                                                                                        |
|-----------------------------------|-----------------------------------|----------------------------------------------------------------------------------------------------|
| otel.trace.exporter=jaeger              | OTEL_TRACE_EXPORTER=jaeger              | Select the Jaeger exporter                                                                         |
| otel.exporter.jaeger.endpoint     | OTEL_EXPORTER_JAEGER_ENDPOINT     | The Jaeger gRPC endpoint to connect to. Default is `localhost:14250`.                              |

### Zipkin exporter
A simple wrapper for the Zipkin exporter of opentelemetry-java. It sends JSON in [Zipkin format](https://zipkin.io/zipkin-api/#/default/post_spans) to a specified HTTP URL.

| System property                   | Environment variable              | Description                                                                                                               |
|-----------------------------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| otel.trace.exporter=zipkin              | OTEL_TRACE_EXPORTER=zipkin              | Select the Zipkin exporter                                                                                             |
| otel.exporter.zipkin.endpoint     | OTEL_EXPORTER_ZIPKIN_ENDPOINT     | The Zipkin endpoint to connect to. Default is `http://localhost:9411/api/v2/spans`. Currently only HTTP is supported. |

### Prometheus exporter
A simple wrapper for the Prometheus exporter of opentelemetry-java.

| System property               | Environment variable          | Description                                                                        |
|-------------------------------|-------------------------------|------------------------------------------------------------------------------------|
| otel.metrics.exporter=prometheus      | OTEL_METRICS_EXPORTER=prometheus      | Select the Prometheus exporter                                                     |
| otel.exporter.prometheus.port | OTEL_EXPORTER_PROMETHEUS_PORT | The local port used to bind the prometheus metric server. Default is `9464`.       |
| otel.exporter.prometheus.host | OTEL_EXPORTER_PROMETHEUS_HOST | The local address used to bind the prometheus metric server. Default is `0.0.0.0`. |

### Logging exporter

The logging exporter prints the name of the span along with its
attributes to stdout. It's mainly used for testing and debugging.

| System property              | Environment variable         | Description                                                                  |
|------------------------------|------------------------------|------------------------------------------------------------------------------|
| otel.trace.exporter=logging        | OTEL_TRACE_EXPORTER=logging        | Select the logging exporter for tracing                                               |
| otel.metrics.exporter=logging        | OTEL_METRICS_EXPORTER=logging        | Select the logging exporter for metrics                                               |
| otel.exporter.logging.prefix | OTEL_EXPORTER_LOGGING_PREFIX | An optional string printed in front of the span name and attributes.         |

## Propagator

The propagators determine which distributed tracing header formats are used, and which baggage propagation header formats are used.

| System property  | Environment variable | Description                                                                                                     |
|------------------|----------------------|-----------------------------------------------------------------------------------------------------------------|
| otel.propagators | OTEL_PROPAGATORS     | The propagators to be used. Use a comma-separated list for multiple propagators. Supported propagators are `tracecontext`, `baggage`, `b3`, `b3multi`, `jaeger`, `ottracer`, and `xray`. Default is `tracecontext,baggage` (W3C). |

## OpenTelemetry Resource

The [OpenTelemetry Resource](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/sdk.md)
is a representation of the entity producing telemetry.

| System property          | Environment variable     | Description                                                                        |
|--------------------------|--------------------------|------------------------------------------------------------------------------------|
| otel.resource.attributes | OTEL_RESOURCE_ATTRIBUTES | Specify resource attributes in the following format: key1=val1,key2=val2,key3=val3 |

## Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes) is the name of a remote service being connected to. It corresponds to `service.name` in the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service) for the local service.

| System property                     | Environment variable              | Description                                                                      |
|------------------------------------|------------------------------------|----------------------------------------------------------------------------------|
| otel.endpoint.peer.service.mapping | OTEL_ENDPOINT_PEER_SERVICE_MAPPING | Used to specify a mapping from hostnames or IP addresses to peer services, as a comma-separated list of host=name pairs. The peer service is added as an attribute to a span whose host or IP match the mapping. For example, if set to 1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have an attribute of `dogs-api`. |

## Batch span processor

| System property           | Environment variable      | Description                                                                        |
|---------------------------|---------------------------|------------------------------------------------------------------------------------|
| otel.bsp.schedule.delay   | OTEL_BSP_SCHEDULE_DELAY   | The interval, in milliseconds, between two consecutive exports. Default is `5000`. |
| otel.bsp.max.queue.size   | OTEL_BSP_MAX_QUEUE_SIZE   | The maximum queue size. Default is `2048`.                                             |
| otel.bsp.max.export.batch.size | OTEL_BSP_MAX_EXPORT_BATCH_SIZE | The maximum batch size. Default is `512`.                                              |
| otel.bsp.export.timeout   | OTEL_BSP_EXPORT_TIMEOUT   | The maximum allowed time, in milliseconds, to export data. Default is `30000`.         |

## Trace config

| System property                 | Environment variable            | Description                                                  |
|---------------------------------|---------------------------------|--------------------------------------------------------------|
| otel.trace.sampler              | OTEL_TRACE_SAMPLER              | The sampler to use for tracing. Defaults to `parentbased_always_on` |
| otel.trace.sampler.arg          | OTEL_TRACE_SAMPLER_ARG          | An argument to the configured tracer if supported, for example a ratio. |
| otel.span.attribute.count.limit | OTEL_SPAN_ATTRIBUTE_COUNT_LIMIT | The maximum number of attributes per span. Default is `32`.  |
| otel.span.event.count.limit     | OTEL_SPAN_EVENT_COUNT_LIMIT     | The maximum number of events per span. Default is `128`.     |
| otel.span.link.count.limit      | OTEL_SPAN_LINK_COUNT_LIMIT      | The maximum number of links per span. Default is `32`        |

Supported values for `otel.trace.sampler` are

- "always_on": AlwaysOnSampler
- "always_off": AlwaysOffSampler
- "traceidratio": TraceIdRatioBased. `otel.trace.sampler.arg` sets the ratio.
- "parentbased_always_on": ParentBased(root=AlwaysOnSampler)
- "parentbased_always_off": ParentBased(root=AlwaysOffSampler)
- "parentbased_traceidratio": ParentBased(root=TraceIdRatioBased). `otel.trace.sampler.arg` sets the ratio.

## Interval metric reader

| System property          | Environment variable     | Description                                                                       |
|--------------------------|--------------------------|-----------------------------------------------------------------------------------|
| otel.imr.export.interval | OTEL_IMR_EXPORT_INTERVAL | The interval, in milliseconds, between pushes to the exporter. Default is `60000`.|

## Customizing the OpenTelemetry SDK

*Customizing the SDK is highly advanced behavior and is still in the prototyping phase. It may change drastically or be removed completely. Use
with caution*

The OpenTelemetry SDK exposes SPI [hooks](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/spi)
for customizing its behavior, such as the `Resource` attached to spans or the `Sampler`.

Because the automatic instrumentation runs in a different classpath than the instrumented application, it is not possible for customization in the application to take advantage of this customization. In order to provide such customization, you can provide the path to a JAR file, including an SPI implementation using the system property `otel.initializer.jar`. Note that this JAR needs to shade the OpenTelemetry API in the same way as the agent does. The simplest way to do this is to use the same shading configuration as the agent from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfade733b899a2f02cfec7033c6a1efd7c54fd8b/java-agent/java-agent.gradle#L39). In addition, you must specify the `io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.spi.TraceProvider` to the name of the class that implements the SPI.

## Suppressing specific auto-instrumentation

See [suppressing specific auto-instrumentation](suppressing-instrumentation.md)
