/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TapirPekkoHttpServerRouteInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public TapirPekkoHttpServerRouteInstrumentationModule() {
    super(
        "pekko-http",
        "pekko-http-1.0",
        "pekko-http-server",
        "pekko-http-server-route",
        "tapir-pekko-http-server",
        "tapir-pekko-http-server-route");
  }

  @Override
  public String getModuleGroup() {
    return "pekko-server";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TapirPathInstrumentation());
  }
}
