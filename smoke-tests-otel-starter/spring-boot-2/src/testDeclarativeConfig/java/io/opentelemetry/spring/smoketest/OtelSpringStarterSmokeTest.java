/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION;

import io.opentelemetry.api.trace.SpanKind;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
class OtelSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired private RestTemplateBuilder restTemplateBuilder;

  // can't use @LocalServerPort annotation since it moved packages between Spring Boot 2 and 3
  @Value("${local.server.port}")
  private int port;

  @Test
  void restTemplate() {
    testing.clearAllExportedData();

    RestTemplate restTemplate = restTemplateBuilder.rootUri("http://localhost:" + port).build();
    restTemplate.getForObject(OtelSpringStarterSmokeTestController.PING, String.class);
    testing.waitAndAssertTraces(
        traceAssert ->
            traceAssert.hasSpansSatisfyingExactly(
                span ->
                    HttpSpanDataAssert.create(span)
                        .assertClientGetRequest("/ping")
                        .hasResourceSatisfying(
                            r ->
                                r.hasAttribute(
                                    // to make sure the declarative config is picked up
                                    // in application.yaml
                                    SERVICE_NAME, "declarative-config-spring-boot-2")),
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasResourceSatisfying(
                            r ->
                                r.hasAttribute(
                                        TELEMETRY_DISTRO_NAME, "opentelemetry-spring-boot-starter")
                                    .hasAttribute(
                                        satisfies(
                                            TELEMETRY_DISTRO_VERSION,
                                            AbstractCharSequenceAssert::isNotBlank))
                                    .hasAttribute(
                                        satisfies(
                                            SERVICE_INSTANCE_ID,
                                            AbstractCharSequenceAssert::isNotBlank)))
                        .hasAttribute(HTTP_ROUTE, "/ping"),
                AbstractSpringStarterSmokeTest::withSpanAssert));
  }
}
