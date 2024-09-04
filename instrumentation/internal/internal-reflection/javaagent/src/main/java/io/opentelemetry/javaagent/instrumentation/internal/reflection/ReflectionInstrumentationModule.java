/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReflectionInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ReflectionInstrumentationModule() {
    super("internal-reflection");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClassInstrumentation(), new ReflectionInstrumentation());
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    // we do not use ByteBuddy Advice dispatching in this instrumentation
    // Instead, we manually call ReflectionHelper via ASM
    // Easiest solution to work with indy is to inject an indy-proxy to be invoked
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.internal.reflection.ReflectionHelper")
        .inject(InjectionMode.CLASS_ONLY);
  }
}
