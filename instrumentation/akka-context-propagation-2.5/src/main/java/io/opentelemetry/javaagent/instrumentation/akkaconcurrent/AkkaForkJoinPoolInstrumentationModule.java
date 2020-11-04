/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaconcurrent;

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
public class AkkaForkJoinPoolInstrumentationModule extends InstrumentationModule {
  public AkkaForkJoinPoolInstrumentationModule() {
    super("akka_context_propagation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AkkaForkJoinPoolInstrumentation(), new AkkaForkJoinTaskInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), State.class.getName());
    map.put(Callable.class.getName(), State.class.getName());
    map.put(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME, State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }
}
