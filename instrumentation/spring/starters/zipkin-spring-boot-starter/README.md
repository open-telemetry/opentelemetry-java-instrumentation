# OpenTelemetry Zipkin Exporter Starter

> **Deprecated:** this starter is deprecated and will be removed in 3.0. Use the
> [OpenTelemetry Spring Boot Starter](../spring-boot-starter/README.md) with the OTLP exporter
> instead. When `otel.instrumentation.common.v3-preview` is enabled, configuring
> `otel.traces.exporter=zipkin` already fails at startup.

The OpenTelemetry Exporter Starter for Java is a starter package that includes packages required to enable tracing using OpenTelemetry. It also provides the dependency and corresponding auto-configuration.

OpenTelemetry Zipkin Exporter Starter is a starter package that includes the opentelemetry-api, opentelemetry-sdk, opentelemetry-extension-annotations, opentelemetry-logging-exporter, opentelemetry-spring-boot-autoconfigurations and spring framework starters required to setup distributed tracing. It also provides the [opentelemetry-exporter-zipkin](https://github.com/open-telemetry/opentelemetry-java/tree/v1.64.0/exporters/zipkin) artifact and corresponding exporter auto-configuration.

Documentation for the OpenTelemetry Zipkin Exporter Starter can be found [here](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/other-spring-autoconfig/#zipkin-starter).
