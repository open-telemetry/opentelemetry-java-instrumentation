/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ScalaConcurrentInstrumentationModule extends InstrumentationModule {
  public ScalaConcurrentInstrumentationModule() {
    super("scala-fork-join", "scala-fork-join-2.8");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ScalaForkJoinPoolInstrumentation(), new ScalaForkJoinTaskInstrumentation());
  }
}
