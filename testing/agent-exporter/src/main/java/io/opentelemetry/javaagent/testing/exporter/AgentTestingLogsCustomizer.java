/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.sdk.appender.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.instrumentation.api.appender.internal.AgentLogEmitterProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogProcessor;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogProcessor;

@AutoService(AgentListener.class)
public class AgentTestingLogsCustomizer implements AgentListener {

  static final LogProcessor logProcessor =
      SimpleLogProcessor.create(AgentTestingExporterFactory.logExporter);

  @Override
  public void beforeAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {

    SdkLogEmitterProvider logEmitterProvider =
        SdkLogEmitterProvider.builder()
            .setResource(autoConfiguredOpenTelemetrySdk.getResource())
            .addLogProcessor(logProcessor)
            .build();

    AgentLogEmitterProvider.resetForTest();
    AgentLogEmitterProvider.set(DelegatingLogEmitterProvider.from(logEmitterProvider));
  }
}
