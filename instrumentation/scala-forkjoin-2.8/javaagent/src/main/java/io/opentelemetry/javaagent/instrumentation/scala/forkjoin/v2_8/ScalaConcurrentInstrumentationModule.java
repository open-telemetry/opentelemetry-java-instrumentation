/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scala.forkjoin.v2_8;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ScalaConcurrentInstrumentationModule extends InstrumentationModule {
  public ScalaConcurrentInstrumentationModule() {
    super("scala-forkjoin-2.8", "scala-forkjoin");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ScalaForkJoinPoolInstrumentation(), new ScalaForkJoinTaskInstrumentation());
  }
}
