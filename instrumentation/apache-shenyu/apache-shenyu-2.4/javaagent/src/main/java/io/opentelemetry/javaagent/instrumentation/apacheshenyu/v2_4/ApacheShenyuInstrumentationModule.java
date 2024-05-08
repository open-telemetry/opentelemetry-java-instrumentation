/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheShenyuInstrumentationModule extends InstrumentationModule {
  public ApacheShenyuInstrumentationModule() {
    super("apache-shenyu", "apache-shenyu-2.4");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ContextBuilderInstrumentation());
  }
}
