/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.appender.api.internal.AgentLogEmitterProvider;
import io.opentelemetry.instrumentation.appender.sdk.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.export.BatchLogProcessor;

@AutoService(AgentListener.class)
public class AgentTestingLogsCustomizer implements AgentListener {

  @Override
  public void beforeAgent(
      Config config, AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {

    SdkLogEmitterProvider logEmitterProvider =
        SdkLogEmitterProvider.builder()
            .setResource(autoConfiguredOpenTelemetrySdk.getResource())
            .addLogProcessor(
                BatchLogProcessor.builder(AgentTestingExporterFactory.logExporter).build())
            .build();

    AgentLogEmitterProvider.set(DelegatingLogEmitterProvider.from(logEmitterProvider));
  }
}
