/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.MapConverterTestAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Spring Boot auto configuration test for {@link OtlpSpanExporterAutoConfiguration}. */
@ExtendWith(OutputCaptureExtension.class)
class OtlpSpanExporterAutoConfigurationTest {

  private final OtlpHttpSpanExporterBuilder otlpHttpSpanExporterBuilder =
      Mockito.mock(OtlpHttpSpanExporterBuilder.class);
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class,
                  OtlpSpanExporterAutoConfiguration.class,
                  MapConverterTestAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize OtlpHttpSpanExporter bean")
  void otlpEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpSpanExporter", OtlpHttpSpanExporter.class))
                    .isNotNull());

    Mockito.verifyNoMoreInteractions(otlpHttpSpanExporterBuilder);
  }

  @Test
  void otlpTracesEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpSpanExporter", OtlpHttpSpanExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize OtlpGrpcSpanExporter bean")
  void otlpDisabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpSpanExporter")).isFalse());
  }

  @Test
  void otlpTracesDisabledOld() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpSpanExporter")).isFalse());
  }

  @Test
  void otlpTracesDisabled() {
    this.contextRunner
        .withPropertyValues("otel.traces.exporter=none")
        .run(context -> assertThat(context.containsBean("otelOtlpSpanExporter")).isFalse());
  }

  @Test
  @DisplayName("when otlp enabled property is MISSING should initialize OtlpHttpSpanExporter bean")
  void exporterPresentByDefault() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("otelOtlpSpanExporter", OtlpHttpSpanExporter.class))
                .isNotNull());
  }

  @Test
  @DisplayName("use http/protobuf when protocol set")
  void useHttp() {
    this.contextRunner
        .withBean(OtlpHttpSpanExporterBuilder.class, () -> otlpHttpSpanExporterBuilder)
        .withPropertyValues(
            "otel.exporter.otlp.enabled=true",
            "otel.exporter.otlp.protocol=http/protobuf",
            "otel.exporter.otlp.endpoint=http://localhost:4317",
            "otel.exporter.otlp.headers.x=1",
            "otel.exporter.otlp.headers.y=2",
            "otel.exporter.otlp.timeout=1s")
        .run(context -> {});

    Mockito.verify(otlpHttpSpanExporterBuilder).build();
    Mockito.verify(otlpHttpSpanExporterBuilder).setEndpoint("http://localhost:4317/v1/traces");
    Mockito.verify(otlpHttpSpanExporterBuilder).addHeader("x", "1");
    Mockito.verify(otlpHttpSpanExporterBuilder).addHeader("y", "2");
    Mockito.verify(otlpHttpSpanExporterBuilder).setTimeout(java.time.Duration.ofSeconds(1));
    Mockito.verifyNoMoreInteractions(otlpHttpSpanExporterBuilder);
  }

  @Test
  @DisplayName("use http/protobuf with environment variables for headers using the MapConverter")
  void useHttpWithEnv() {
    this.contextRunner
        .withBean(OtlpHttpSpanExporterBuilder.class, () -> otlpHttpSpanExporterBuilder)
        .withPropertyValues(
            "otel.exporter.otlp.enabled=true", "otel.exporter.otlp.protocol=http/protobuf")
        // are similar to environment variables in that they use the same converters
        .withSystemProperties("otel.exporter.otlp.headers=x=1,y=2%203")
        .run(context -> {});

    Mockito.verify(otlpHttpSpanExporterBuilder).build();
    Mockito.verify(otlpHttpSpanExporterBuilder).addHeader("x", "1");
    Mockito.verify(otlpHttpSpanExporterBuilder).addHeader("y", "2 3");
    Mockito.verifyNoMoreInteractions(otlpHttpSpanExporterBuilder);
  }

  @Test
  @DisplayName("OTLP header with illegal % encoding causes an exception")
  void decodingError(CapturedOutput output) {
    this.contextRunner
        .withBean(OtlpHttpSpanExporterBuilder.class, () -> otlpHttpSpanExporterBuilder)
        .withSystemProperties("otel.exporter.otlp.headers=x=%-1")
        .run(context -> {});

    // spring catches the exception and logs it
    assertThat(output).contains("Cannot decode header value: %-1");
  }

  @Test
  @DisplayName("use grpc when protocol set")
  void useGrpc() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.protocol=grpc")
        .run(
            context ->
                assertThat(context.getBean(OtlpGrpcSpanExporter.class))
                    .as("Should contain the gRPC span exporter when grpc is set")
                    .isNotNull());
  }

  @Test
  @DisplayName("use http when unknown protocol set")
  void useHttpWhenAnUnknownProtocolIsSet() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.protocol=unknown")
        .run(
            context ->
                assertThat(context.getBean(OtlpHttpSpanExporter.class))
                    .as("Should contain the http span exporter when an unknown is set")
                    .isNotNull());
  }

  @Test
  @DisplayName("logging exporter can still be configured")
  void loggingExporter() {
    this.contextRunner
        .withBean(LoggingSpanExporter.class, LoggingSpanExporter::create)
        .run(
            context ->
                assertThat(
                        context.getBeanProvider(SpanExporter.class).stream()
                            .collect(Collectors.toList()))
                    .hasSize(2));
  }
}
