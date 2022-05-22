/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * An {@link AgentListener} that enables oshi metrics during agent startup if oshi is present on the
 * system classpath.
 */
@AutoService(AgentListener.class)
public class OshiMetricsInstaller implements AgentListener {

  private static final boolean DEFAULT_ENABLED =
      Config.get().getBoolean("otel.instrumentation.common.default-enabled", true);

  @Override
  public void afterAgent(Config config, AutoConfiguredOpenTelemetrySdk unused) {
    if (new AgentConfig(config)
        .isInstrumentationEnabled(Collections.singleton("oshi"), DEFAULT_ENABLED)) {
      try {
        // Call oshi.SystemInfo.getCurrentPlatformEnum() to activate SystemMetrics.
        // Oshi instrumentation will intercept this call and enable SystemMetrics.
        Class<?> oshiSystemInfoClass =
            ClassLoader.getSystemClassLoader().loadClass("oshi.SystemInfo");
        Method getCurrentPlatformEnumMethod = getCurrentPlatformMethod(oshiSystemInfoClass);
        getCurrentPlatformEnumMethod.invoke(null);
      } catch (Throwable ex) {
        // OK
      }
    }
  }

  private static Method getCurrentPlatformMethod(Class<?> oshiSystemInfoClass)
      throws NoSuchMethodException {
    try {
      return oshiSystemInfoClass.getMethod("getCurrentPlatformEnum");
    } catch (NoSuchMethodException exception) {
      // renamed in oshi 6.0.0
      return oshiSystemInfoClass.getMethod("getCurrentPlatform");
    }
  }
}
