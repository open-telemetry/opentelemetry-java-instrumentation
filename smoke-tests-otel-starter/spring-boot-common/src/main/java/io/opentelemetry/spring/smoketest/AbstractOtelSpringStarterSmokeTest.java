/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.PropagationProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.SpringConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.Collections;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * This test class enforces the order of the tests to make sure that {@link #shouldSendTelemetry()},
 * which asserts the telemetry data from the application startup, is executed first.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AbstractOtelSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private Environment environment;
  @Autowired private PropagationProperties propagationProperties;
  @Autowired private OtelResourceProperties otelResourceProperties;
  @Autowired private OtlpExporterProperties otlpExporterProperties;

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {

    @Bean
    @Order(1)
    AutoConfigurationCustomizerProvider hiddenPropagatorCustomizer() {
      return customizer ->
          customizer.addResourceCustomizer(
              (resource, config) ->
                  resource.merge(
                      Resource.create(
                          Attributes.of(
                              AttributeKey.booleanKey("keyFromResourceCustomizer"), false))));
    }

    @Bean
    @Order(2)
    AutoConfigurationCustomizerProvider propagatorCustomizer() {
      return customizer ->
          customizer.addResourceCustomizer(
              (resource, config) ->
                  resource.merge(
                      Resource.create(
                          Attributes.of(
                              AttributeKey.booleanKey("keyFromResourceCustomizer"), true))));
    }
  }

  @Test
  void propertyConversion() {
    ConfigProperties configProperties =
        SpringConfigProperties.create(
            environment,
            otlpExporterProperties,
            otelResourceProperties,
            propagationProperties,
            DefaultConfigProperties.createFromMap(
                Collections.singletonMap("otel.exporter.otlp.headers", "a=1,b=2")));
    assertThat(configProperties.getMap("otel.exporter.otlp.headers"))
        .containsEntry("a", "1")
        .containsEntry("b", "2")
        .containsEntry("c", "3");
    assertThat(configProperties.getList("otel.propagators")).containsExactly("b3");
  }

  @Test
  @org.junit.jupiter.api.Order(1)
  void shouldSendTelemetry() {
    testRestTemplate.getForObject(OtelSpringStarterSmokeTestController.PING, String.class);

    // Span
    testing.waitAndAssertTraces(
        traceAssert ->
            traceAssert.hasSpansSatisfyingExactly(
                spanDataAssert ->
                    spanDataAssert
                        .hasKind(SpanKind.CLIENT)
                        .hasAttribute(
                            DbIncubatingAttributes.DB_STATEMENT,
                            "create table test_table (id bigint not null, primary key (id))")),
        traceAssert ->
            traceAssert.hasSpansSatisfyingExactly(
                clientSpan ->
                    clientSpan
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith("/ping")),
                serverSpan ->
                    serverSpan
                        .hasKind(SpanKind.SERVER)
                        .hasResourceSatisfying(
                            r ->
                                r.hasAttribute(
                                        AttributeKey.booleanKey("keyFromResourceCustomizer"), true)
                                    .hasAttribute(
                                        AttributeKey.stringKey("attributeFromYaml"), "true")
                                    .hasAttribute(
                                        OpenTelemetryAssertions.satisfies(
                                            ServiceIncubatingAttributes.SERVICE_INSTANCE_ID,
                                            AbstractCharSequenceAssert::isNotBlank)))
                        .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                        .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping")));

    // Metric
    testing.waitAndAssertMetrics(
        OtelSpringStarterSmokeTestController.METER_SCOPE_NAME,
        OtelSpringStarterSmokeTestController.TEST_HISTOGRAM,
        AbstractIterableAssert::isNotEmpty);

    // Log
    LogRecordData firstLog = testing.getExportedLogRecords().get(0);
    assertThat(firstLog.getBody().asString())
        .as("Should instrument logs")
        .startsWith("Starting ")
        .contains(this.getClass().getSimpleName());
    assertThat(firstLog.getAttributes().asMap())
        .as("Should capture code attributes")
        .containsEntry(
            CodeIncubatingAttributes.CODE_NAMESPACE, "org.springframework.boot.StartupInfoLogger");
  }

  @Test
  void restTemplate() {
    assertClient(OtelSpringStarterSmokeTestController.REST_TEMPLATE);
  }

  protected void assertClient(String url) {
    testing.clearAllExportedData();

    testRestTemplate.getForObject(url, String.class);

    testing.waitAndAssertTraces(
        traceAssert ->
            traceAssert.hasSpansSatisfyingExactly(
                clientSpan ->
                    clientSpan
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith(url)),
                serverSpan ->
                    serverSpan
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, url),
                nestedClientSpan ->
                    nestedClientSpan
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith("/ping")),
                nestedServerSpan ->
                    nestedServerSpan
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping")));
  }
}
