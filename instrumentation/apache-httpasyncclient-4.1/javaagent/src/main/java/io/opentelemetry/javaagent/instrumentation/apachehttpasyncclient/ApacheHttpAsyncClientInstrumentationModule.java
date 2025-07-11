/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheHttpAsyncClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ApacheHttpAsyncClientInstrumentationModule() {
    super("apache-httpasyncclient", "apache-httpasyncclient-4.1");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApacheHttpAsyncClientInstrumentation());
  }
}
