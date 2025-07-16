/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.internal.ConfigPropertiesUtil;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.instrument.Instrumentation;

/** Installs the {@link JarAnalyzer}. */
@AutoService(BeforeAgentListener.class)
public class JarAnalyzerInstaller implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties config =
        ConfigPropertiesUtil.resolveConfigProperties(autoConfiguredOpenTelemetrySdk);

    boolean enabled =
        config.getBoolean("otel.instrumentation.runtime-telemetry.package-emitter.enabled", false);
    if (!enabled) {
      return;
    }
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return;
    }
    int jarsPerSecond =
        config.getInt("otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second", 10);
    JarAnalyzer jarAnalyzer =
        JarAnalyzer.create(autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk(), jarsPerSecond);
    inst.addTransformer(jarAnalyzer);
  }
}
