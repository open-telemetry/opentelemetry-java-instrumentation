/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OkHttp2InstrumentationModule extends InstrumentationModule {
  public OkHttp2InstrumentationModule() {
    super("okhttp", "okhttp-2.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new OkHttpClientInstrumentation(), new DispatcherInstrumentation());
  }
}
