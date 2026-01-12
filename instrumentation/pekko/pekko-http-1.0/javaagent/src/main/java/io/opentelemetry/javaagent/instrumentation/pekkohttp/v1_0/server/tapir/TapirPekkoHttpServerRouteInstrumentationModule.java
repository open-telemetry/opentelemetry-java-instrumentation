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
        "pekko_http",
        "pekko_http_1.0",
        "pekko_http_server",
        "pekko_http_server_route",
        "tapir_pekko_http_server",
        "tapir_pekko_http_server_route");
  }

  @Override
  public String getModuleGroup() {
    return "pekko-server";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TapirPathInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
