/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaclassloader;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule {
  public ClassLoaderInstrumentationModule() {
    super("classloader");
  }

  @Override
  protected String[] additionalHelperClassNames() {
    return new String[] {"io.opentelemetry.javaagent.tooling.Constants"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ClassLoaderInstrumentation(),
        new UrlClassLoaderInstrumentation(),
        new ProxyInstrumentation(),
        new ResourceInjectionInstrumentation());
  }
}
