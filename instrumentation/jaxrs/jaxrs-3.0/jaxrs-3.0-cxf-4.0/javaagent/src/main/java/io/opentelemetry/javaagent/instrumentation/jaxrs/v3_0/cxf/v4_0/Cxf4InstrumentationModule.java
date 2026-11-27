/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Cxf4InstrumentationModule extends InstrumentationModule {
  public Cxf4InstrumentationModule() {
    super("jaxrs-3.0-cxf-4.0", "jaxrs-cxf", "jaxrs");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return unmodifiableList(
        asList(
            new CxfRequestContextInstrumentation(),
            new CxfServletControllerInstrumentation(),
            new CxfJaxRsInvokerInstrumentation()));
  }
}
