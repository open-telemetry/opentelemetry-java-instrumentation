/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class NonStandardExecutorsInstrumentationModule extends InstrumentationModule {

  public NonStandardExecutorsInstrumentationModule() {
    super("java_concurrent.other");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OtherExecutorsInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Runnable.class.getName(), State.class.getName());
  }

  private static final class OtherExecutorsInstrumentation extends AbstractExecutorInstrumentation {
    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

      transformers.put( // org.eclipse.jetty.util.thread.QueuedThreadPool
          named("dispatch").and(takesArguments(1)).and(takesArgument(0, Runnable.class)),
          JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
      return transformers;
    }
  }
}
