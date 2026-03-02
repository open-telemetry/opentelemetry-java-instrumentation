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
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

/** Installs the {@link JarAnalyzer}. */
@AutoService(BeforeAgentListener.class)
public class JarAnalyzerInstaller implements BeforeAgentListener {

  private static final Logger logger = Logger.getLogger(JarAnalyzerInstaller.class.getName());
  private static final int DEFAULT_JARS_PER_SECOND = 10;

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");

    // Support both new config (package_emitter/development) and old path
    // (package_emitter)
    DeclarativeConfigProperties newPackageEmitterConfig = config.get("package_emitter/development");
    DeclarativeConfigProperties oldPackageEmitterConfig = config.get("package_emitter");

    boolean enabledNew = newPackageEmitterConfig.getBoolean("enabled", false);
    boolean enabledOld = oldPackageEmitterConfig.getBoolean("enabled", false);
    if (enabledOld) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.package-emitter.enabled is deprecated and will"
              + " be removed in 3.0. Use"
              + " otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled"
              + " instead.");
    }
    if (!enabledNew && !enabledOld) {
      return;
    }
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return;
    }

    // Use appropriate instrumentation name based on which config is active
    String instrumentationName =
        enabledNew
            ? "io.opentelemetry.runtime-telemetry"
            : "io.opentelemetry.runtime-telemetry-java8";

    int newJarsPerSecond = newPackageEmitterConfig.getInt("jars_per_second", -1);
    int oldJarsPerSecond = oldPackageEmitterConfig.getInt("jars_per_second", -1);

    if (oldJarsPerSecond >= 0) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second is deprecated"
              + " and will be removed in 3.0. Use"
              + " otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second"
              + " instead.");
    }

    int jarsPerSecond;
    if (newJarsPerSecond >= 0) {
      jarsPerSecond = newJarsPerSecond;
    } else if (oldJarsPerSecond >= 0) {
      jarsPerSecond = oldJarsPerSecond;
    } else {
      jarsPerSecond = DEFAULT_JARS_PER_SECOND;
    }

    JarAnalyzer jarAnalyzer = JarAnalyzer.create(openTelemetry, instrumentationName, jarsPerSecond);
    inst.addTransformer(jarAnalyzer);
  }
}
