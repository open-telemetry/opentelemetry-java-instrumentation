/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule {
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
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new BootDelegationInstrumentation(),
        new LoadInjectedClassInstrumentation(),
        new ResourceInjectionInstrumentation(),
        new DefineClassInstrumentation());
  }
}
