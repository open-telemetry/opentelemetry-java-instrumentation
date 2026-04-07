/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CxfInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public CxfInstrumentationModule() {
    super("cxf", "cxf-3.0", "jaxws");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JaxWsServerFactoryBeanInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
