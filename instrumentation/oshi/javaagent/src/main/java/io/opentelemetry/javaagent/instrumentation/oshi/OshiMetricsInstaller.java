/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/**
 * An {@link AgentListener} that enables oshi metrics during agent startup if oshi is present on the
 * system classpath.
 */
@AutoService(AgentListener.class)
public class OshiMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    AgentDistributionConfig config = AgentDistributionConfig.get();
    if (!config.isInstrumentationEnabled("oshi")) {
      return;
    }

    try {
      // Instantiate oshi.SystemInfo to activate SystemMetrics.
      // Oshi instrumentation will intercept this call and enable SystemMetrics.
      // (The static getCurrentPlatformEnum()/getCurrentPlatform() entry points used in older
      // versions were both removed in oshi 7.0.0.)
      Class<?> oshiSystemInfoClass =
          ClassLoader.getSystemClassLoader().loadClass("oshi.SystemInfo");
      oshiSystemInfoClass.getConstructor().newInstance();
    } catch (Throwable t) {
      // OK
    }
  }
}
