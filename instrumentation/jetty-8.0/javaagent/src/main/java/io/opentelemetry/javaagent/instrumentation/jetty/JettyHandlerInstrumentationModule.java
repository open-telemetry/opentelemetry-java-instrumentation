/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JettyHandlerInstrumentationModule extends InstrumentationModule {

  public JettyHandlerInstrumentationModule() {
    super("jetty", "jetty-8.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HandlerInstrumentation(), new JettyQueuedThreadPoolInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Runnable.class.getName(), State.class.getName());
  }

  public static class HandlerInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.eclipse.jetty.server.Handler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // skipping built-in handlers, so that for servlets there will be no span started by jetty.
      // this is so that the servlet instrumentation will capture contextPath and servletPath
      // normally, which the jetty instrumentation does not capture since jetty doesn't populate
      // contextPath and servletPath until right before calling the servlet
      // (another option is to instrument ServletHolder.handle() to capture those fields)
      return not(named("org.eclipse.jetty.server.handler.HandlerWrapper"))
          .and(not(named("org.eclipse.jetty.server.handler.ScopedHandler")))
          .and(not(named("org.eclipse.jetty.server.handler.ContextHandler")))
          .and(not(named("org.eclipse.jetty.servlet.ServletHandler")))
          .and(implementsInterface(named("org.eclipse.jetty.server.Handler")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          named("handle")
              // need to capture doHandle() for handlers that extend built-in handlers excluded
              // above
              .or(named("doHandle"))
              .and(takesArgument(0, named("java.lang.String")))
              .and(takesArgument(1, named("org.eclipse.jetty.server.Request")))
              .and(takesArgument(2, named("javax.servlet.http.HttpServletRequest")))
              .and(takesArgument(3, named("javax.servlet.http.HttpServletResponse")))
              .and(isPublic()),
          JettyHandlerAdvice.class.getName());
    }
  }

  public static class JettyQueuedThreadPoolInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.eclipse.jetty.util.thread.QueuedThreadPool");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

      transformers.put(
          named("dispatch").and(takesArguments(1)).and(takesArgument(0, Runnable.class)),
          JettyHandlerInstrumentationModule.class.getName() + "$SetExecuteRunnableStateAdvice");
      return transformers;
    }
  }

  public static class SetExecuteRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(newTask)) {
        task = newTask;
        ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, newTask, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }
}
