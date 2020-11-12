/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaconcurrent;

import static io.opentelemetry.javaagent.instrumentation.scalaconcurrent.ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@AutoService(InstrumentationModule.class)
public class ScalaConcurrentInstrumentationModule extends InstrumentationModule {
  public ScalaConcurrentInstrumentationModule() {
    super("java_concurrent", "scala_concurrent");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ScalaForkJoinPoolInstrumentation(), new ScalaForkJoinTaskInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), State.class.getName());
    map.put(Callable.class.getName(), State.class.getName());
    map.put(TASK_CLASS_NAME, State.class.getName());
    return Collections.unmodifiableMap(map);
  }
}
