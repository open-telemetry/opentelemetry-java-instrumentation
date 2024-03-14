/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isEnum;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.HttpUrl;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Query;
import retrofit2.Retrofit;

public class InfluxDbImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.influxdb.impl.InfluxDBImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("query"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, named("org.influxdb.dto.Query")))
                    .or(
                        takesArguments(2)
                            .and(takesArgument(0, named("org.influxdb.dto.Query")))
                            .and(takesArgument(1, named("java.util.concurrent.TimeUnit"))))
                    .or(
                        takesArguments(3)
                            .and(takesArgument(0, named("org.influxdb.dto.Query")))
                            .and(takesArgument(1, int.class))
                            .and(takesArgument(2, Consumer.class)))
                    .or(
                        takesArguments(3)
                            .and(takesArgument(0, named("org.influxdb.dto.Query")))
                            .and(takesArgument(1, Consumer.class))
                            .and(takesArgument(2, Consumer.class)))
                    .or(
                        takesArguments(5)
                            .and(takesArgument(0, named("org.influxdb.dto.Query")))
                            .and(takesArgument(1, int.class))
                            .and(takesArgument(2, BiConsumer.class))
                            .and(takesArgument(3, Runnable.class))
                            .and(takesArgument(4, Consumer.class)))),
        this.getClass().getName() + "$InfluxDbQueryAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("write"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, named("org.influxdb.dto.BatchPoints")))
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
                            .and(takesArgument(3, named("java.util.concurrent.TimeUnit"))))),
        this.getClass().getName() + "$InfluxDbModifyAdvice");
  }

  @SuppressWarnings("unused")
  public static class InfluxDbQueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Query query,
        @Advice.Origin("#m") String methodName,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (query == null) {
        return;
      }
      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      InetSocketAddress inetSocketAddress = new InetSocketAddress(httpUrl.host(), httpUrl.port());
      influxDbRequest =
          InfluxDbRequest.create(
              inetSocketAddress,
              httpUrl.uri().toString(),
              query.getDatabase(),
              query.getCommand(),
              methodName);

      if (!instrumenter().shouldStart(parentContext, influxDbRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, influxDbRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      scope.close();

      instrumenter().end(context, influxDbRequest, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbModifyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(0) Object arg0,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(Retrofit.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      if (arg0 == null) {
        return;
      }

      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      InetSocketAddress inetSocketAddress = new InetSocketAddress(httpUrl.host(), httpUrl.port());
      String database =
          (arg0 instanceof BatchPoints) ? ((BatchPoints) arg0).getDatabase() : String.valueOf(arg0);

      influxDbRequest =
          InfluxDbRequest.create(
              inetSocketAddress, httpUrl.uri().toString(), database, methodName, methodName);

      if (!instrumenter().shouldStart(parentContext, influxDbRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, influxDbRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope == null) {
        return;
      }
      scope.close();

      instrumenter().end(context, influxDbRequest, null, throwable);
    }
  }
}
