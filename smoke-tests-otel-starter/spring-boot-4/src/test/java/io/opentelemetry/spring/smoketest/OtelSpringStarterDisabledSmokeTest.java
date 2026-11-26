/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"otel.sdk.disabled=true"})
@AutoConfigureRestTestClient
@DisabledInNativeImage // Without this the native tests in the OtelSpringStarterSmokeTest class will
// fail with org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "CUSTOMER" already exists
class OtelSpringStarterDisabledSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired private RestTestClient restTestClient;

  @Autowired private ConfigProvider configProvider;

  @Test
  void shouldExposeNoopConfigProvider() {
    assertThat(configProvider).isEqualTo(ConfigProvider.noop());
  }

  @Test
  void shouldNotSendTelemetry() throws InterruptedException {
    restTestClient
        .get()
        .uri(OtelSpringStarterSmokeTestController.PING)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("pong");

    // See SpringSmokeOtelConfiguration
    Thread.sleep(200);

    List<SpanData> exportedSpans = testing.getExportedSpans();
    assertThat(exportedSpans).isEmpty();

    List<MetricData> exportedMetrics = testing.getExportedMetrics();
    assertThat(exportedMetrics).isEmpty();

    List<LogRecordData> exportedLogRecords = testing.getExportedLogRecords();
    assertThat(exportedLogRecords).isEmpty();
  }
}
