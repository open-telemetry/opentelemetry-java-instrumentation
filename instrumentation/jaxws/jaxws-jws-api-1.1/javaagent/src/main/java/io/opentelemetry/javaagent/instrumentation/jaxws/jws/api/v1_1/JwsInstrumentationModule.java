/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.api.v1_1;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JwsInstrumentationModule extends InstrumentationModule {

  public JwsInstrumentationModule() {
    super("jaxws-jws-api", "jaxws-jws-api-1.1", "jaxws");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JwsAnnotationsInstrumentation());
  }

  @Override
  public boolean defaultEnabled() {
    // this instrumentation only produces controller telemetry
    return super.defaultEnabled() && ExperimentalConfig.get().controllerTelemetryEnabled();
  }
}
