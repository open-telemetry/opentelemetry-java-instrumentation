/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JaxWsInstrumentationModule extends InstrumentationModule {

  public JaxWsInstrumentationModule() {
    super(
        "jaxws",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"jaxws-2.0", "jaxws-2.0-core"}
            : new String[] {"jaxws-2.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WebServiceProviderInstrumentation());
  }
}
