/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

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
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

@AutoService(InstrumentationModule.class)
public class JavaConcurrentInstrumentationModule extends InstrumentationModule {
  public JavaConcurrentInstrumentationModule() {
    super("java_concurrent");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CallableInstrumentation(),
        new FutureInstrumentation(),
        new JavaExecutorInstrumentation(),
        new JavaForkJoinTaskInstrumentation(),
        new RunnableInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put(Callable.class.getName(), State.class.getName());
    map.put(ForkJoinTask.class.getName(), State.class.getName());
    map.put(Future.class.getName(), State.class.getName());
    map.put(Runnable.class.getName(), State.class.getName());
    return Collections.unmodifiableMap(map);
  }
}
