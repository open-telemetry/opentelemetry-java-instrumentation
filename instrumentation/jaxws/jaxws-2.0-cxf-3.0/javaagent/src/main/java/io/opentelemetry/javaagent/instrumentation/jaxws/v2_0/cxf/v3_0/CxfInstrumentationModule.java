/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.cxf.v3_0;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CxfInstrumentationModule extends InstrumentationModule {
  public CxfInstrumentationModule() {
    super("cxf", expandDeprecatedNames("jaxws-2.0-cxf-3.0|deprecated:jaxws-cxf-3.0", "jaxws"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JaxWsServerFactoryBeanInstrumentation());
  }
}
