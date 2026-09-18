/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix.v1_4;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HystrixInstrumentationModule extends InstrumentationModule {

  public HystrixInstrumentationModule() {
    super("hystrix", "hystrix-1.4");
  }

  @Override
  public boolean defaultEnabled() {
    return super.defaultEnabled() && !AgentCommonConfig.get().isV3Preview();
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("rx.OpenTelemetryTracingUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HystrixCommandInstrumentation());
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("rx.OpenTelemetryTracingUtil");
  }
}
