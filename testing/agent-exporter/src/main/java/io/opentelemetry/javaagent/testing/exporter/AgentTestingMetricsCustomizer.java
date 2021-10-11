/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.time.Duration;

@AutoService(SdkMeterProviderConfigurer.class)
public class AgentTestingMetricsCustomizer implements SdkMeterProviderConfigurer {
  @Override
  public void configure(
      SdkMeterProviderBuilder sdkMeterProviderBuilder, ConfigProperties configProperties) {
    sdkMeterProviderBuilder.registerMetricReader(
        PeriodicMetricReader.create(
            AgentTestingExporterFactory.metricExporter, Duration.ofMillis(100)));
  }
}
