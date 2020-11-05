/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JerseyInstrumentationModule extends InstrumentationModule {
  public JerseyInstrumentationModule() {
    super("jax-rs", "jaxrs", "jersey");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable",
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsTracer",
      packageName + ".RequestContextHelper"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JerseyRequestContextInstrumentation());
  }
}
