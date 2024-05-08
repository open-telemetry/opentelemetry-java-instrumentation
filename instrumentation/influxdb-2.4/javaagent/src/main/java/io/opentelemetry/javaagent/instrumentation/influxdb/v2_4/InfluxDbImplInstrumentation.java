/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbConstants.CREATE_DATABASE_STATEMENT_NEW;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbConstants.CREATE_DATABASE_STATEMENT_OLD;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbConstants.DELETE_DATABASE_STATEMENT;
import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isEnum;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
    public static void onEnter(
        @Advice.Argument(0) Query query,
        @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] arguments,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      if (query == null) {
        return;
      }
      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      influxDbRequest =
          InfluxDbRequest.create(
              httpUrl.host(), httpUrl.port(), query.getDatabase(), query.getCommand());

      if (!instrumenter().shouldStart(parentContext, influxDbRequest)) {
        return;
      }

      // wrap callbacks so they'd run in the context of the parent span
      Object[] newArguments = new Object[arguments.length];
      boolean hasChangedArgument = false;
      for (int i = 0; i < arguments.length; i++) {
        newArguments[i] = InfluxDbObjetWrapper.wrap(arguments[i], parentContext);
        hasChangedArgument |= newArguments[i] != arguments[i];
      }
      if (hasChangedArgument) {
        arguments = newArguments;
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

  @SuppressWarnings("unused")
  public static class InfluxDbModifyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This InfluxDBImpl influxDbImpl,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(0) Object arg0,
        @Advice.FieldValue(value = "retrofit") Retrofit retrofit,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") InfluxDbRequest influxDbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(InfluxDBImpl.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      if (arg0 == null) {
        return;
      }

      Context parentContext = currentContext();

      HttpUrl httpUrl = retrofit.baseUrl();
      String database =
          (arg0 instanceof BatchPoints)
              ? ((BatchPoints) arg0).getDatabase()
              // write data by UDP protocol, in this way, can't get database name.
              : arg0 instanceof Integer ? "" : String.valueOf(arg0);

      String sql = methodName;
      if ("createDatabase".equals(methodName)) {
        sql =
            influxDbImpl.version().startsWith("0.")
                ? String.format(CREATE_DATABASE_STATEMENT_OLD, database)
                : String.format(CREATE_DATABASE_STATEMENT_NEW, database);
      } else if ("deleteDatabase".equals(methodName)) {
        sql = String.format(DELETE_DATABASE_STATEMENT, database);
      }

      influxDbRequest = InfluxDbRequest.create(httpUrl.host(), httpUrl.port(), database, sql);

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
