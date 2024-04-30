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
import io.opentelemetry.spring.smoketest.AbstractSpringStarterSmokeTest;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestController;
import io.opentelemetry.spring.smoketest.SpringSmokeOtelConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
    classes = {OtelSpringStarterSmokeTestApplication.class, SpringSmokeOtelConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractMongodbSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  void mongodb() {
    testing.clearAllExportedData(); // ignore data from application startup

    String url = OtelSpringStarterSmokeTestController.MONGODB;
    testRestTemplate.getForObject(url, String.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith(url)),
                span -> span.hasKind(SpanKind.SERVER).hasAttribute(HttpAttributes.HTTP_ROUTE, url),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasName("find test.customer")
                        .hasAttribute(
                            DbIncubatingAttributes.DB_SYSTEM,
                            DbIncubatingAttributes.DbSystemValues.MONGODB)));
  }
}
