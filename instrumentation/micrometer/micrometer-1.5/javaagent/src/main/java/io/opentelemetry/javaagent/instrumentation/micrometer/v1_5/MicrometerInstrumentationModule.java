/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MicrometerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public MicrometerInstrumentationModule() {
    super("micrometer", "micrometer-1.5");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // produces a lot of metrics that are already captured - e.g. JVM memory usage
    return false;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.5
    return hasClassesNamed("io.micrometer.core.instrument.config.validate.Validated");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new MetricsInstrumentation(), new AbstractCompositeMeterInstrumentation());
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    // we use asm to call a method in MicrometerSingletons
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.MicrometerSingletons")
        .inject(InjectionMode.CLASS_ONLY);
  }
}
