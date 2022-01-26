/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule {
  public ClassLoaderInstrumentationModule() {
    super("internal-class-loader");
  }

  @Override
  public boolean defaultEnabled() {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("io.opentelemetry.javaagent.tooling.Constants");
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return singletonList(
        "io.opentelemetry.javaagent.instrumentation.internal.classloader.DefineClassUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ClassLoaderInstrumentation(),
        new ResourceInjectionInstrumentation(),
        new DefineClassInstrumentation());
  }
}
