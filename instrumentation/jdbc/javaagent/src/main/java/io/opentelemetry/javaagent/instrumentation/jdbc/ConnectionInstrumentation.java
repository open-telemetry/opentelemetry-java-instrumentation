/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.transactionInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.Connection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Connection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("prepare")
            .and(takesArgument(0, String.class))
            // Also include CallableStatement, which is a sub type of PreparedStatement
            .and(returns(implementsInterface(named("java.sql.PreparedStatement")))),
        ConnectionInstrumentation.class.getName() + "$PrepareAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("commit", "rollback").and(takesNoArguments()).and(isPublic()),
        ConnectionInstrumentation.class.getName() + "$TransactionAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDbInfo(
        @Advice.Argument(0) String sql, @Advice.Return PreparedStatement statement) {
      JdbcData.preparedStatement.set(statement, sql);
    }
  }

  @SuppressWarnings("unused")
  public static class TransactionAdvice {

    public static final class AdviceScope {
      private final DbRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(DbRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Connection connection, String methodName) {
        DbRequest request =
            DbRequest.createTransaction(connection, methodName.toUpperCase(Locale.ROOT));
        if (request == null) {
          return null;
        }
        Context parentContext = currentContext();
        if (!transactionInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = transactionInstrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        transactionInstrumenter().end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This Connection connection, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(connection, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) adviceScope.end(throwable);
    }
  }
}
