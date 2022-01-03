/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import java.sql.SQLException;
import java.sql.Statement;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StatementInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.Statement");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Statement"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        StatementInstrumentation.class.getName() + "$StatementAdvice");
  }

  @SuppressWarnings("unused")
  public static class StatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String sql,
        @Advice.This Statement statement,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") DbRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      // Connection#getMetaData() may execute a Statement or PreparedStatement to retrieve DB info
      // this happens before the DB CLIENT span is started (and put in the current context), so this
      // instrumentation runs again and the shouldStartSpan() check always returns true - and so on
      // until we get a StackOverflowError
      // using CallDepth prevents this, because this check happens before Connection#getMetadata()
      // is called - the first recursive Statement call is just skipped and we do not create a span
      // for it
      // the call depth should be anchored to the specific connection of this statement to avoid
      // suppressing spans for layered JDBC drivers (that should be performed by semantic
      // suppression)
      // it's possible however that getting the connection throws an SQLException hence falling back
      // to the call depth of a generic Statement.
      try {
        callDepth = CallDepth.forClass(statement.getConnection().getClass());
      } catch (SQLException e) {
        callDepth = CallDepth.forClass(Statement.class);
      }
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = currentContext();
      request = DbRequest.create(statement, sql);

      if (request == null || !instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") DbRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        instrumenter().end(context, request, null, throwable);
      }
    }
  }
}
