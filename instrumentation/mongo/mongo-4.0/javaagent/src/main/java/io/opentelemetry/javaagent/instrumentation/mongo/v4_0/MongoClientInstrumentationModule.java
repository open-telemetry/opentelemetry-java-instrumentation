/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.AsyncCompletionHandler;
import com.mongodb.event.CommandListener;
import com.mongodb.internal.async.SingleResultCallback;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.AdviceUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.instrumentation.mongo.TracingCommandListener;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MongoClientInstrumentationModule extends InstrumentationModule {

  public MongoClientInstrumentationModule() {
    super("mongo", "mongo-4.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientSettingsBuilderInstrumentation(),
        new AsyncCompletionHandlerInstrumentation(),
        new BaseClusterInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.mongodb.connection.AsyncCompletionHandler", State.class.getName());
  }

  private static final class MongoClientSettingsBuilderInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.MongoClientSettings$Builder")
          .and(
              declaresMethod(
                  named("addCommandListener")
                      .and(
                          takesArguments(
                              new TypeDescription.Latent(
                                  "com.mongodb.event.CommandListener",
                                  Modifier.PUBLIC,
                                  null,
                                  Collections.<TypeDescription.Generic>emptyList())))
                      .and(isPublic())));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
          MongoClientInstrumentationModule.class.getName() + "$MongoClientAdvice");
    }
  }

  public static class MongoClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(
        @Advice.This MongoClientSettings.Builder builder,
        @Advice.FieldValue("commandListeners") List<CommandListener> commandListeners) {
      for (CommandListener commandListener : commandListeners) {
        if (commandListener instanceof TracingCommandListener) {
          return;
        }
      }
      builder.addCommandListener(new TracingCommandListener());
    }
  }

  private static final class AsyncCompletionHandlerInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("com.mongodb.connection.AsyncCompletionHandler"));
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("com.mongodb.connection.AsyncCompletionHandler");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isConstructor(), MongoClientInstrumentationModule.class.getName() + "$SetupStateAdvice");
      transformers.put(
          isMethod().and(isPublic()).and(named("completed")).and(takesArguments(1)),
          MongoClientInstrumentationModule.class.getName() + "$TaskScopeAdvice");
      transformers.put(
          isMethod().and(isPublic()).and(named("failed")).and(takesArguments(Throwable.class)),
          MongoClientInstrumentationModule.class.getName() + "$TaskScopeAdvice");
      return transformers;
    }
  }

  public static class SetupStateAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static State setupState(@Advice.This AsyncCompletionHandler asyncCompletionHandler) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(asyncCompletionHandler)) {
        ContextStore<AsyncCompletionHandler, State> contextStore =
            InstrumentationContext.get(AsyncCompletionHandler.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, asyncCompletionHandler, Java8BytecodeBridge.currentContext());
      }
      return null;
    }
  }

  public static class TaskScopeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.This AsyncCompletionHandler asyncCompletionHandler) {
      ContextStore<AsyncCompletionHandler, State> contextStore =
          InstrumentationContext.get(AsyncCompletionHandler.class, State.class);
      return AdviceUtils.startTaskScope(contextStore, asyncCompletionHandler);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  private static final class BaseClusterInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.internal.connection.BaseCluster");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPublic())
              .and(named("selectServerAsync"))
              .and(takesArgument(0, named("com.mongodb.selector.ServerSelector")))
              .and(takesArgument(1, named("com.mongodb.internal.async.SingleResultCallback"))),
          MongoClientInstrumentationModule.class.getName() + "$ServerSelectionAdvice");
    }
  }

  public static class ServerSelectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 1, readOnly = false) SingleResultCallback callback) {
      callback = new ServerSelectionCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
