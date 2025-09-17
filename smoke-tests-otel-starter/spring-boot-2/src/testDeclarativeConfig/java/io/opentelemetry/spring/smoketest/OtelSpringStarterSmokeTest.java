/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;

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

    org.springframework.web.client.RestTemplate restTemplate =
        restTemplateBuilder.rootUri("http://localhost:" + port).build();
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
                                    ServiceAttributes.SERVICE_NAME,
                                    "declarative-config-spring-boot-2")),
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasResourceSatisfying(
                            r ->
                                r.hasAttribute(
                                        TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME,
                                        "opentelemetry-spring-boot-starter")
                                    .hasAttribute(
                                        satisfies(
                                            TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION,
                                            AbstractCharSequenceAssert::isNotBlank))
                                    .hasAttribute(
                                        satisfies(
                                            ServiceIncubatingAttributes.SERVICE_INSTANCE_ID,
                                            AbstractCharSequenceAssert::isNotBlank)))
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping"),
                AbstractSpringStarterSmokeTest::withSpanAssert));
  }
}
