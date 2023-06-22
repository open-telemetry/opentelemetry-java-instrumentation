/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;

/**
 * This test relies on {@link OpenTelemetryAppender} initializing {@link GlobalOpenTelemetry} via
 * autoconfigure. {@code log4j2-use-global.xml} opts into using {@link GlobalOpenTelemetry} by
 * setting {@link OpenTelemetryAppender.Builder#setUseGlobalOpenTelemetry(boolean)}. The {@code
 * testUseGlobalOpenTelemetry} gradle task used to run this test opts into {@link
 * GlobalOpenTelemetry} triggering autoconfigure by setting {@code
 * -Dotel.java.global-autoconfigure.enabled=true}.
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class OpenTelemetryAppenderGlobalTest extends AbstractOpenTelemetryConfigTest
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    logRecordExporter = InMemoryLogRecordExporter.create();
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");

    autoConfiguration.addLoggerProviderCustomizer(
        (sdkLoggerProviderBuilder, configProperties) ->
            SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter)));
  }
}
