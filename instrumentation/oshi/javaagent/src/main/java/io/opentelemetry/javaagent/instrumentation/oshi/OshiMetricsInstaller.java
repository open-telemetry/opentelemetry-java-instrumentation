/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.reflect.Method;

/**
 * An {@link AgentListener} that enables oshi metrics during agent startup if oshi is present on the
 * system classpath.
 */
@AutoService(AgentListener.class)
public class OshiMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    boolean enabled =
        DeclarativeConfigUtil.getBoolean(
                autoConfiguredSdk.getOpenTelemetrySdk(), "java", "oshi", "enabled")
            .orElseGet(
                () ->
                    DeclarativeConfigUtil.getBoolean(
                            autoConfiguredSdk.getOpenTelemetrySdk(),
                            "java",
                            "common",
                            "default_enabled")
                        .orElse(true));
    if (!enabled) {
      return;
    }

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
