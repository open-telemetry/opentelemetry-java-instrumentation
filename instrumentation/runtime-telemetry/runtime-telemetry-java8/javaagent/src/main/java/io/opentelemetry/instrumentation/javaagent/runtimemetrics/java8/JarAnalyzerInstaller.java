/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import com.google.auto.service.AutoService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import net.bytebuddy.agent.builder.AgentBuilder;

/** Installs the {@link JarAnalyzer}. */
@AutoService({AgentExtension.class, AgentListener.class})
public class JarAnalyzerInstaller implements AgentExtension, AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    JarAnalyzer.getInstance().maybeInstall(autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk());
  }

  @Override
  @CanIgnoreReturnValue
  public AgentBuilder extend(AgentBuilder agentBuilder, ConfigProperties config) {
    boolean enabled =
        config.getBoolean("otel.instrumentation.runtime-telemetry.package-emitter.enabled", false);
    if (!enabled) {
      return agentBuilder;
    }
    JarAnalyzer jarAnalyzer = JarAnalyzer.getInstance();
    int jarsPerSecond =
        config.getInt("otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second", 10);
    jarAnalyzer.configure(jarsPerSecond);
    Instrumentation inst = InstrumentationHolder.getInstrumentation();
    if (inst == null) {
      return agentBuilder;
    }
    inst.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(
              ClassLoader loader,
              String className,
              Class<?> classBeingRedefined,
              ProtectionDomain protectionDomain,
              byte[] classfileBuffer) {
            jarAnalyzer.handle(protectionDomain);
            return null;
          }
        });
    return agentBuilder;
  }

  @Override
  public String extensionName() {
    return "jar-analyzer";
  }
}
