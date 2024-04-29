/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestController;
import io.opentelemetry.spring.smoketest.SpringSmokeInstrumentationExtension;
import io.opentelemetry.spring.smoketest.SpringSmokeOtelConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractMongodbSpringStarterSmokeTest {

  @RegisterExtension
  static final SpringSmokeInstrumentationExtension testing =
      SpringSmokeInstrumentationExtension.create();

  @Autowired private TestRestTemplate testRestTemplate;

  @AfterEach
  void tearDown(CapturedOutput output) {
    assertThat(output).doesNotContain("WARN").doesNotContain("ERROR");
  }

  @Test
  void mongodb() {
    String url = OtelSpringStarterSmokeTestController.MONGODB;
    testRestTemplate.getForObject(url, String.class);

    testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith(url)),
                    span ->
                        span.hasKind(SpanKind.SERVER).hasAttribute(HttpAttributes.HTTP_ROUTE, url),
                    span ->
                        span.hasKind(SpanKind.CLIENT)
                            .hasName("find test.customer")
                            .hasAttribute(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.MONGODB)));
  }
}
