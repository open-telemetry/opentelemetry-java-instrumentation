/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @BeforeAll
  static void setupAll() {
    logRecordExporter = InMemoryLogRecordExporter.create();
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
            .build();
    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ((OpenTelemetryAppender)
            loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("OpenTelemetry"))
        .setOpenTelemetry(openTelemetrySdk);
  }
}
