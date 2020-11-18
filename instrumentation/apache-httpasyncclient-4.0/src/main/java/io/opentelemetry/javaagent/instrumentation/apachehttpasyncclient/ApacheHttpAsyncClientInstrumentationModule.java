/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheHttpAsyncClientInstrumentationModule extends InstrumentationModule {
  public ApacheHttpAsyncClientInstrumentationModule() {
    super("apache-httpasyncclient", "apache-httpasyncclient-4.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter",
      ApacheHttpAsyncClientInstrumentation.class.getName() + "$DelegatingRequestProducer",
      ApacheHttpAsyncClientInstrumentation.class.getName() + "$TraceContinuedFutureCallback",
      packageName + ".ApacheHttpAsyncClientTracer"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ApacheHttpAsyncClientInstrumentation(), new ApacheHttpClientRedirectInstrumentation());
  }
}
