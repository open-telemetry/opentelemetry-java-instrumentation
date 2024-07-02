/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JettyHttpClient12InstrumentationModule extends InstrumentationModule {
  public JettyHttpClient12InstrumentationModule() {
    super("jetty-httpclient", "jetty-httpclient-12.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JettyHttpClient12Instrumentation(),
        new JettyClient12ResponseListenersInstrumentation());
  }
}
