/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbcmysql.v0_8;

import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import dev.miku.r2dbc.mysql.client.Client;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class QueryFlowInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return isPackagePrivate().and(named("dev.miku.r2dbc.mysql.QueryFlow"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPackagePrivate().and(isStatic()).and(named("login")),
        QueryFlowInstrumentation.class.getName() + "$LoginAdvice");
    transformer.applyAdviceToMethod(
        isStatic()
            .and(named("execute"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("dev.miku.r2dbc.mysql.TextQuery")))
            .and(takesArgument(2, List.class)),
        QueryFlowInstrumentation.class.getName() + "$TextQueryExecuteAdvice");
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(1, String.class)),
        QueryFlowInstrumentation.class.getName() + "$SingleSqlExecuteAdvice");
    transformer.applyAdviceToMethod(
        named("executeVoid").and(takesArgument(1, String.class)),
        QueryFlowInstrumentation.class.getName() + "$SingleSqlExecuteAdvice");

    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(1, List.class)),
        QueryFlowInstrumentation.class.getName() + "$MultiStatementExecuteAdvice");
    transformer.applyAdviceToMethod(
        named("executeVoid").and(takesArgument(1, String[].class)),
        QueryFlowInstrumentation.class.getName() + "$MultiStatementExecuteVoidAdvice");
  }

  @SuppressWarnings("unused")
  public static class LoginAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addDbInfo(
        @Advice.Argument(0) Client client,
        @Advice.Argument(2) String database,
        @Advice.Argument(3) String user) {
      DbInfo dbInfo = VirtualField.find(Client.class, DbInfo.class).get(client);
      DbInfo.Builder builder = dbInfo == null ? DbInfo.builder() : dbInfo.toBuilder();
      VirtualField.find(Client.class, DbInfo.class)
          .set(client, builder.db(database).user(user).build());
    }
  }

  @SuppressWarnings("unused")
  public static class MultiStatementExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Client client,
        @Advice.Argument(1) List<String> statements,
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Origin() Method method,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      DbInfo dbInfo = VirtualField.find(Client.class, DbInfo.class).get(client);
      dbRequest = DbRequest.create(dbInfo, String.join(",", statements.toArray(new String[0])));
      Instrumenter<DbRequest, Void> instrumenter = R2dbcSingletons.instrumenter();
      Context current = Java8BytecodeBridge.currentContext();
      if (instrumenter.shouldStart(current, dbRequest)) {
        context = instrumenter.start(current, dbRequest);
        scope = context.makeCurrent();
        operationEndSupport =
            AsyncOperationEndSupport.create(instrumenter, Void.class, method.getReturnType());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      returnValue = operationEndSupport.asyncEnd(context, dbRequest, returnValue, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class MultiStatementExecuteVoidAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Client client,
        @Advice.Argument(1) String[] statements,
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Origin() Method method,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      DbInfo dbInfo = VirtualField.find(Client.class, DbInfo.class).get(client);
      dbRequest = DbRequest.create(dbInfo, String.join(",", statements));
      Instrumenter<DbRequest, Void> instrumenter = R2dbcSingletons.instrumenter();
      Context current = Java8BytecodeBridge.currentContext();
      if (instrumenter.shouldStart(current, dbRequest)) {
        context = instrumenter.start(current, dbRequest);
        scope = context.makeCurrent();
        operationEndSupport =
            AsyncOperationEndSupport.create(instrumenter, Void.class, method.getReturnType());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      returnValue = operationEndSupport.asyncEnd(context, dbRequest, returnValue, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SingleSqlExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Client client,
        @Advice.Argument(1) String sql,
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Origin() Method method,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      DbInfo dbInfo = VirtualField.find(Client.class, DbInfo.class).get(client);
      dbRequest = DbRequest.create(dbInfo, sql);
      Instrumenter<DbRequest, Void> instrumenter = R2dbcSingletons.instrumenter();
      Context current = Java8BytecodeBridge.currentContext();
      if (instrumenter.shouldStart(current, dbRequest)) {
        context = instrumenter.start(current, dbRequest);
        scope = context.makeCurrent();
        operationEndSupport =
            AsyncOperationEndSupport.create(instrumenter, Void.class, method.getReturnType());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      returnValue = operationEndSupport.asyncEnd(context, dbRequest, returnValue, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class TextQueryExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Client client,
        @Advice.Argument(1) Object query,
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Origin() Method method,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      DbInfo dbInfo = VirtualField.find(Client.class, DbInfo.class).get(client);
      String sql = VirtualField.find(Object.class, String.class).get(query);
      dbRequest = DbRequest.create(dbInfo, sql);
      Instrumenter<DbRequest, Void> instrumenter = R2dbcSingletons.instrumenter();
      Context current = Java8BytecodeBridge.currentContext();
      if (instrumenter.shouldStart(current, dbRequest)) {
        context = instrumenter.start(current, dbRequest);
        scope = context.makeCurrent();
        operationEndSupport =
            AsyncOperationEndSupport.create(instrumenter, Void.class, method.getReturnType());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("operationEndSupport")
            AsyncOperationEndSupport<DbRequest, Void> operationEndSupport,
        @Advice.Local("dbRequest") DbRequest dbRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      returnValue = operationEndSupport.asyncEnd(context, dbRequest, returnValue, throwable);
    }
  }
}
