/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpserver;

import static java.util.Arrays.asList;

import java.util.List;

import com.google.auto.service.AutoService;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

@AutoService(InstrumentationModule.class)
public class JdkHttpServerInstrumentationModule extends InstrumentationModule {
  public JdkHttpServerInstrumentationModule() {
    super("java-http-server");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new JdkServerContextInstrumentation());
  }
}
