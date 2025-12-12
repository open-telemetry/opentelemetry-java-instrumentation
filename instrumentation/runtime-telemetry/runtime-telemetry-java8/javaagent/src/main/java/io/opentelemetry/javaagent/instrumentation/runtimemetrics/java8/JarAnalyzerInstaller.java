/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimemetrics.java8;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.instrument.Instrumentation;

/** Installs the {@link JarAnalyzer}. */
@AutoService(BeforeAgentListener.class)
public class JarAnalyzerInstaller implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    boolean enabled =
        DeclarativeConfigUtil.getBoolean(
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(),
                "java",
                "runtime_telemetry",
                "package_emitter",
                "enabled")
            .orElse(false);
    if (!enabled) {
      return;
    }
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return;
    }
    int jarsPerSecond =
        DeclarativeConfigUtil.getInt(
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(),
                "java",
                "runtime_telemetry",
                "package_emitter",
                "jars_per_second")
            .orElse(10);
    JarAnalyzer jarAnalyzer =
        JarAnalyzer.create(autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(), jarsPerSecond);
    inst.addTransformer(jarAnalyzer);
  }
}
