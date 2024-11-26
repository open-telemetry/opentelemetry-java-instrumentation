/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"otel.instrumentation.logback-appender.enabled=false"})
@DisabledInNativeImage // Without this the native tests in the OtelSpringStarterSmokeTest class will
// fail with org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "CUSTOMER" already exists
class OtelSpringStarterWithLogbackInstrumentationDisabledSmokeTest
    extends AbstractSpringStarterSmokeTest {

  @Test
  void shouldNotSendLogRecordTelemetry() throws InterruptedException {

    // See SpringSmokeOtelConfiguration
    Thread.sleep(200);

    List<LogRecordData> exportedLogRecords = testing.getExportedLogRecords();
    assertThat(exportedLogRecords).isEmpty();
  }
}
