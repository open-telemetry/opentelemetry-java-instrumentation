/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HttpClientInstrumentationModule extends InstrumentationModule {
  public HttpClientInstrumentationModule() {
    super("httpclient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter",
      packageName + ".JdkHttpClientTracer",
      packageName + ".ResponseConsumer"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpClientInstrumentation(), new HttpHeadersInstrumentation());
  }
}
