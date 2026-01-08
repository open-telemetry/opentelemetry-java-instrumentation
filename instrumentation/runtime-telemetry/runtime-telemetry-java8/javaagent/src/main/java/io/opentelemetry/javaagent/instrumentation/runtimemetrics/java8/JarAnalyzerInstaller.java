/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimemetrics.java8;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.instrument.Instrumentation;

/** Installs the {@link JarAnalyzer}. */
@AutoService(BeforeAgentListener.class)
public class JarAnalyzerInstaller implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ExtendedDeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(), "runtime_telemetry")
            .get("package_emitter");
    if (!config.getBoolean("enabled", false)) {
      return;
    }
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return;
    }
    JarAnalyzer jarAnalyzer =
        JarAnalyzer.create(
            autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(),
            config.getInt("jars_per_second", 10));
    inst.addTransformer(jarAnalyzer);
  }
}
