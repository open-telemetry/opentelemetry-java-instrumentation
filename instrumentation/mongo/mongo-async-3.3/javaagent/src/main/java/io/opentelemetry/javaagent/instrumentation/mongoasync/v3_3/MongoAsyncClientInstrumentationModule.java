/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.event.CommandListener;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MongoAsyncClientInstrumentationModule extends InstrumentationModule {

  public MongoAsyncClientInstrumentationModule() {
    super("mongo-async", "mongo-async-3.3", "mongo");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientSettingsBuildersInstrumentation(),
        new InternalStreamConnectionInstrumentation(),
        new BaseClusterInstrumentation());
  }

  private static final class MongoClientSettingsBuildersInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.async.client.MongoClientSettings$Builder")
          .and(
              declaresMethod(
                  named("addCommandListener")
                      .and(isPublic())
                      .and(
                          takesArguments(1)
                              .and(takesArgument(0, named("com.mongodb.event.CommandListener"))))))
          .and(declaresField(named("commandListeners")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
          MongoAsyncClientInstrumentationModule.class.getName() + "$MongoAsyncClientAdvice");
    }
  }

  public static class MongoAsyncClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(
        @Advice.This MongoClientSettings.Builder builder,
        @Advice.FieldValue("commandListeners") List<CommandListener> commandListeners) {
      for (CommandListener commandListener : commandListeners) {
        if (commandListener == MongoInstrumentationSingletons.LISTENER) {
          return;
        }
      }
      builder.addCommandListener(MongoInstrumentationSingletons.LISTENER);
    }
  }

  private static final class InternalStreamConnectionInstrumentation
      implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.connection.InternalStreamConnection");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isMethod()
              .and(named("openAsync"))
              .and(takesArgument(0, named("com.mongodb.async.SingleResultCallback"))),
          MongoAsyncClientInstrumentationModule.class.getName()
              + "$SingleResultCallbackArg0Advice");
      transformers.put(
          isMethod()
              .and(named("readAsync"))
              .and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
          MongoAsyncClientInstrumentationModule.class.getName()
              + "$SingleResultCallbackArg1Advice");
      transformers.put(
          isMethod()
              .and(named("sendMessageAsync"))
              .and(takesArgument(2, named("com.mongodb.async.SingleResultCallback"))),
          MongoAsyncClientInstrumentationModule.class.getName()
              + "$SingleResultCallbackArg2Advice");
      return transformers;
    }
  }

  private static final class BaseClusterInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.mongodb.connection.BaseCluster");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPublic())
              .and(named("selectServerAsync"))
              .and(takesArgument(0, named("com.mongodb.selector.ServerSelector")))
              .and(takesArgument(1, named("com.mongodb.async.SingleResultCallback"))),
          MongoAsyncClientInstrumentationModule.class.getName()
              + "$SingleResultCallbackArg1Advice");
    }
  }

  public static class SingleResultCallbackArg0Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 0, readOnly = false) SingleResultCallback<Object> callback) {
      callback = new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  public static class SingleResultCallbackArg1Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 1, readOnly = false) SingleResultCallback<Object> callback) {
      callback = new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  public static class SingleResultCallbackArg2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 2, readOnly = false) SingleResultCallback<Object> callback) {
      callback = new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
