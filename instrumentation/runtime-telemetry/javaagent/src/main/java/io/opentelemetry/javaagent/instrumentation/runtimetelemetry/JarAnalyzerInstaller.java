/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JarAnalyzerConfig;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.instrument.Instrumentation;

/** Installs the {@link JarAnalyzer}. */
@AutoService(BeforeAgentListener.class)
public class JarAnalyzerInstaller implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");

    // Support both new config (package_emitter/development) and old path
    // (package_emitter)
    DeclarativeConfigProperties newPackageEmitterConfig = config.get("package_emitter/development");
    DeclarativeConfigProperties oldPackageEmitterConfig = config.get("package_emitter");

    String instrumentationName =
        JarAnalyzerConfig.getInstrumentationName(newPackageEmitterConfig, oldPackageEmitterConfig);
    if (instrumentationName == null) {
      return;
    }
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return;
    }

    int jarsPerSecond =
        JarAnalyzerConfig.getJarsPerSecond(newPackageEmitterConfig, oldPackageEmitterConfig);

    JarAnalyzer jarAnalyzer = JarAnalyzer.create(openTelemetry, instrumentationName, jarsPerSecond);
    inst.addTransformer(jarAnalyzer);
  }
}
