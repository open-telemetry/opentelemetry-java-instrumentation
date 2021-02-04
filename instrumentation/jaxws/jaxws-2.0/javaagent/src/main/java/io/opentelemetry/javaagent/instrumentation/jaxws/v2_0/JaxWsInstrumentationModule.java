/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JaxWsInstrumentationModule extends InstrumentationModule {

  public JaxWsInstrumentationModule() {
    super("jaxws", "jaxws-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new WebServiceProviderInstrumentation());
  }
}
