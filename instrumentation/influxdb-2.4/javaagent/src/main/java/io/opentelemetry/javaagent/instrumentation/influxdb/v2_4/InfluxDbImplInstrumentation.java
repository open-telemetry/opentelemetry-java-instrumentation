/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.requestInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isEnum;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbQueryAdviceScope.QueryMethod;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.HttpUrl;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

class InfluxDbImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.influxdb.impl.InfluxDBImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("query")
            .and(
                takesArguments("org.influxdb.dto.Query")
                    .or(
                        takesArguments(
                            "org.influxdb.dto.Query", "java.util.concurrent.TimeUnit"))),
        getClass().getName() + "$InfluxDbQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(
                takesArguments(
                    "org.influxdb.dto.Query",
                    "java.util.function.Consumer",
                    "java.util.function.Consumer")),
        getClass().getName() + "$InfluxDbAsyncQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(takesArguments("org.influxdb.dto.Query", "int", "java.util.function.Consumer")),
        getClass().getName() + "$InfluxDbChunkedConsumerQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(takesArguments("org.influxdb.dto.Query", "int", "java.util.function.BiConsumer")),
        getClass().getName() + "$InfluxDbChunkedBiConsumerQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(
                takesArguments(
                    "org.influxdb.dto.Query",
                    "int",
                    "java.util.function.Consumer",
                    "java.lang.Runnable")),
        getClass().getName() + "$InfluxDbChunkedConsumerCompletionQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(
                takesArguments(
                    "org.influxdb.dto.Query",
                    "int",
                    "java.util.function.BiConsumer",
                    "java.lang.Runnable")),
        getClass().getName() + "$InfluxDbChunkedBiConsumerCompletionQueryAdvice");
    transformer.applyAdviceToMethod(
        named("query")
            .and(
                takesArguments(
                    "org.influxdb.dto.Query",
                    "int",
                    "java.util.function.BiConsumer",
                    "java.lang.Runnable",
                    "java.util.function.Consumer")),
        getClass().getName() + "$InfluxDbChunkedBiConsumerCompletionErrorQueryAdvice");

    transformer.applyAdviceToMethod(
        named("write")
            .and(
                takesArguments("org.influxdb.dto.BatchPoints")
                    .or(takesArguments(2).and(takesArgument(0, int.class)))
                    .or(
                        takesArguments(4)
                            .and(takesArgument(0, String.class))
                            .and(takesArgument(1, String.class))
                            .and(takesArgument(2, isEnum())))
                    .or(
                        takesArguments(5)
                            .and(takesArgument(0, String.class))
                            .and(takesArgument(1, String.class))
                            .and(takesArgument(2, isEnum()))
                            .and(takesArgument(3, TimeUnit.class)))),
        getClass().getName() + "$InfluxDbModifyAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("createDatabase", "deleteDatabase"),
        getClass().getName() + "$InfluxDbModifyAdvice");
  }

  @SuppressWarnings("unused")
  public static class InfluxDbQueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static InfluxDbQueryAdviceScope onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      return InfluxDbQueryAdviceScope.start(query, retrofit, false, QueryMethod.SYNCHRONOUS);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable QueryResult result,
        @Advice.Enter @Nullable InfluxDbQueryAdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, result);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbAsyncQueryAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 1, index = 1),
      @ToArgument(value = 2, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(1) @Nullable Consumer<?> successCallback,
        @Advice.Argument(2) @Nullable Consumer<?> failureCallback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(query, retrofit, true, QueryMethod.ASYNCHRONOUS);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, successCallback, failureCallback};
      }

      return new Object[] {
        adviceScope,
        InfluxDbObjectWrapper.wrapConsumer(successCallback, parentContext, queryState),
        InfluxDbObjectWrapper.wrapConsumer(failureCallback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbChunkedConsumerQueryAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(2) @Nullable Consumer<?> callback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(query, retrofit, true, QueryMethod.CHUNKED_CONSUMER);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, callback};
      }
      return new Object[] {
        adviceScope, InfluxDbObjectWrapper.wrapConsumer(callback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbChunkedBiConsumerQueryAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(2) @Nullable BiConsumer<?, ?> callback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(query, retrofit, true, QueryMethod.CHUNKED_BI_CONSUMER);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, callback};
      }
      return new Object[] {
        adviceScope, InfluxDbObjectWrapper.wrapBiConsumer(callback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbChunkedConsumerCompletionQueryAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 2, index = 1),
      @ToArgument(value = 3, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(2) @Nullable Consumer<?> callback,
        @Advice.Argument(3) @Nullable Runnable completionCallback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(
              query, retrofit, true, QueryMethod.CHUNKED_CONSUMER_COMPLETION);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, callback, completionCallback};
      }
      return new Object[] {
        adviceScope,
        InfluxDbObjectWrapper.wrapConsumer(callback, parentContext, queryState),
        InfluxDbObjectWrapper.wrapRunnable(completionCallback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbChunkedBiConsumerCompletionQueryAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 2, index = 1),
      @ToArgument(value = 3, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(2) @Nullable BiConsumer<?, ?> callback,
        @Advice.Argument(3) @Nullable Runnable completionCallback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(
              query, retrofit, true, QueryMethod.CHUNKED_BI_CONSUMER_COMPLETION);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, callback, completionCallback};
      }
      return new Object[] {
        adviceScope,
        InfluxDbObjectWrapper.wrapBiConsumer(callback, parentContext, queryState),
        InfluxDbObjectWrapper.wrapRunnable(completionCallback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbChunkedBiConsumerCompletionErrorQueryAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 2, index = 1),
      @ToArgument(value = 3, index = 2),
      @ToArgument(value = 4, index = 3)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable Query query,
        @Advice.Argument(2) @Nullable BiConsumer<?, ?> callback,
        @Advice.Argument(3) @Nullable Runnable completionCallback,
        @Advice.Argument(4) @Nullable Consumer<?> errorCallback,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      InfluxDbQueryAdviceScope adviceScope =
          InfluxDbQueryAdviceScope.start(
              query, retrofit, true, QueryMethod.CHUNKED_BI_CONSUMER_COMPLETION_ERROR);
      Context parentContext = adviceScope.parentContext();
      InfluxDbQueryState queryState = adviceScope.queryState();
      if (parentContext == null || queryState == null) {
        return new Object[] {adviceScope, callback, completionCallback, errorCallback};
      }
      return new Object[] {
        adviceScope,
        InfluxDbObjectWrapper.wrapBiConsumer(callback, parentContext, queryState),
        InfluxDbObjectWrapper.wrapRunnable(completionCallback, parentContext, queryState),
        InfluxDbObjectWrapper.wrapConsumer(errorCallback, parentContext, queryState)
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs != null) {
        ((InfluxDbQueryAdviceScope) enterArgs[0]).end(throwable, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbModifyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static InfluxDbScope<InfluxDbOperation> onEnter(
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(0) Object arg0,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.getAndIncrement() > 0) {
        return null;
      }

      if (arg0 == null) {
        return null;
      }

      Context parentContext = Context.current();

      HttpUrl httpUrl = retrofit.baseUrl();
      String database;
      if (arg0 instanceof BatchPoints) {
        database = ((BatchPoints) arg0).getDatabase();
      } else if (arg0 instanceof Integer) {
        // write data by UDP protocol, in this way, can't get database name.
        database = null;
      } else {
        database = String.valueOf(arg0);
      }

      String operationName;
      if ("createDatabase".equals(methodName)) {
        // createDatabase emits a CREATE DATABASE query.
        operationName = "CREATE DATABASE";
      } else if ("deleteDatabase".equals(methodName)) {
        // deleteDatabase emits a DROP DATABASE query.
        operationName = "DROP DATABASE";
      } else {
        operationName = methodName;
      }

      InfluxDbOperation influxDbOperation =
          InfluxDbOperation.create(httpUrl.host(), httpUrl.port(), database, operationName);

      if (!requestInstrumenter().shouldStart(parentContext, influxDbOperation)) {
        return null;
      }

      return InfluxDbScope.start(requestInstrumenter(), parentContext, influxDbOperation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable InfluxDbScope<InfluxDbOperation> scope) {
      CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
