/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheCamelInstrumentationModule extends InstrumentationModule {

  public ApacheCamelInstrumentationModule() {
    super("apache-camel", "apache-camel-2.20");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CamelContextInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.aws.");
  }
}
