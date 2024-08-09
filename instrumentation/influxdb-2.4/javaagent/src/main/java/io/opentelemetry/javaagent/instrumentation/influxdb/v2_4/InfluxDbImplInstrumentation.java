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
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.HttpUrl;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

public class InfluxDbImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.influxdb.impl.InfluxDBImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("query")).and(takesArgument(0, named("org.influxdb.dto.Query"))),
        this.getClass().getName() + "$InfluxDbQueryAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("write"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, named("org.influxdb.dto.BatchPoints")))
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
                            .and(takesArgument(3, named("java.util.concurrent.TimeUnit"))))),
        this.getClass().getName() + "$InfluxDbModifyAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(namedOneOf("createDatabase", "deleteDatabase")),
        this.getClass().getName() + "$InfluxDbModifyAdvice");
  }

  @SuppressWarnings("unused")
  public static class InfluxDbQueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToAllArguments(index = 0, typing = Assigner.Typing.DYNAMIC)
    public static Object[] onEnter(
        @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] arguments,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit) {
      CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.getAndIncrement() > 0) {
        return null;
      }

      Query query = arguments[0] instanceof Query ? (Query) arguments[0] : null;
      if (query == null) {
        return null;
      }
      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      InfluxDbRequest influxDbRequest =
          InfluxDbRequest.create(
              httpUrl.host(), httpUrl.port(), query.getDatabase(), null, query.getCommand());

      if (!instrumenter().shouldStart(parentContext, influxDbRequest)) {
        return null;
      }

      // wrap callbacks so they'd run in the context of the parent span
      Object[] newArguments = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        newArguments[i] = InfluxDbObjetWrapper.wrap(arguments[i], parentContext);
      }

      return new Object[] {newArguments, InfluxDbScope.start(parentContext, influxDbRequest)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter Object[] enterArgs) {
      CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.decrementAndGet() > 0 || enterArgs == null) {
        return;
      }

      ((InfluxDbScope) enterArgs[1]).end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class InfluxDbModifyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static InfluxDbScope onEnter(
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

      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      String database =
          (arg0 instanceof BatchPoints)
              ? ((BatchPoints) arg0).getDatabase()
              // write data by UDP protocol, in this way, can't get database name.
              : arg0 instanceof Integer ? "" : String.valueOf(arg0);

      String operation;
      if ("createDatabase".equals(methodName)) {
        operation = "CREATE DATABASE";
      } else if ("deleteDatabase".equals(methodName)) {
        operation = "DROP DATABASE";
      } else {
        operation = "WRITE";
      }

      InfluxDbRequest influxDbRequest =
          InfluxDbRequest.create(httpUrl.host(), httpUrl.port(), database, operation, null);

      if (!instrumenter().shouldStart(parentContext, influxDbRequest)) {
        return null;
      }

      return InfluxDbScope.start(parentContext, influxDbRequest);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter InfluxDbScope scope) {
      CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
