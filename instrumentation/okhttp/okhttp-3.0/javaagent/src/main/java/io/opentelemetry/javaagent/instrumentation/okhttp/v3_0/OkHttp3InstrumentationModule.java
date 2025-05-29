/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OkHttp3InstrumentationModule extends InstrumentationModule {

  public OkHttp3InstrumentationModule() {
    super("okhttp", "okhttp-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new OkHttp3Instrumentation(), new OkHttp3DispatcherInstrumentation());
  }
}
