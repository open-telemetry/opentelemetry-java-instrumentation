/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ClassLoaderInstrumentationModule() {
    super("internal-class-loader");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public boolean isHelperClass(String className) {
    // TODO: this can be removed when we drop inlined-advice support
    // The advices can directly access this class in the AgentClassLoader with invokedynamic Advice
    return className.equals("io.opentelemetry.javaagent.tooling.Constants");
  }

  @Override
  public boolean loadAdviceClassesEagerly() {
    // This is required due to DefineClassInstrumentation
    // Without this, we would get an infinite recursion on bootstrapping of that instrumentation:
    // 1. ClassLoader.defineClass is invoked somewhere
    // 2. The inserted invokedynamic for the instrumentation is reached
    // 3. To bootstrap the advice, IndyBootstrap is invoked
    // 4. IndyBootstrap tries to load the DefineClassInstrumentation Advice class
    // 5. The loading calls ClassLoader.defineClass -> recursion, BOOM!
    // We avoid this recursion by ensuring that the DefineClassInstrumentation Advice class
    // is loaded eagerly before the corresponding invokedynamic is reached
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new BootDelegationInstrumentation(),
        new LoadInjectedClassInstrumentation(),
        new ResourceInjectionInstrumentation());
  }
}
