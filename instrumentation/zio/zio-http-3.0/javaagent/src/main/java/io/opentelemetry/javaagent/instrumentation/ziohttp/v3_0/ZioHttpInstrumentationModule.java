/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ZioHttpInstrumentationModule extends InstrumentationModule {

  public ZioHttpInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "zio-http-3.0" : "zio-http",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"zio-http", "zio"}
            : new String[] {"zio-http-3.0", "zio"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RoutePatternInstrumentation());
  }
}
