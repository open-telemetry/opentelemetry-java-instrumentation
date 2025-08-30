/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CatsEffectInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public CatsEffectInstrumentationModule() {
    super("cats-effect", "cats-effect-3.6");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(new IoFiberInstrumentation(), new IoRuntimeInstrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("cats.effect.IO")
        // missing before 3.6.0
        .and(hasClassesNamed("cats.effect.unsafe.metrics.IORuntimeMetrics"));
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    return super.defaultEnabled(config)
        && config.getBoolean("cats.effect.trackFiberContext", false);
  }

  @Override
  public String getModuleGroup() {
    return "opentelemetry-api-bridge";
  }

  // ensure it's the last one
  @Override
  public int order() {
    return Integer.MAX_VALUE;
  }
}
