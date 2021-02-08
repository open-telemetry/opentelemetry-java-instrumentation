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
public class CxfInstrumentationModule extends InstrumentationModule {
  public CxfInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "cxf", "cxf-3.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new CxfRequestContextInstrumentation());
  }
}
