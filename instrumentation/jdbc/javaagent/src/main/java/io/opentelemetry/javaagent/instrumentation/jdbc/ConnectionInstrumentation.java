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
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
            // Also include CallableStatement, which is a subtype of PreparedStatement
            .and(returns(implementsInterface(named("java.sql.PreparedStatement")))),
        ConnectionInstrumentation.class.getName() + "$PrepareAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("commit", "rollback").and(takesNoArguments()).and(isPublic()),
        ConnectionInstrumentation.class.getName() + "$TransactionAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareAdvice {

    public static final class PrepareContext implements ImplicitContextKeyed {

      private static final ContextKey<PrepareContext> KEY =
          ContextKey.named("jdbc-prepare-context");

      private final String originalSql;

      private PrepareContext(String originalSql) {
        this.originalSql = originalSql;
      }

      public String get() {
        return originalSql;
      }

      @Nullable
      public static PrepareContext get(Context context) {
        return context.get(KEY);
      }

      public static Context init(Context context, String originalSql) {
        if (context.get(KEY) != null) {
          return context;
        }
        return context.with(new PrepareContext(originalSql));
      }

      @Override
      public Context storeInContext(Context context) {
        return context.with(KEY, this);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] processSql(
        @Advice.This Connection connection, @Advice.Argument(0) String sql) {
      Context context = Java8BytecodeBridge.currentContext();
      if (PrepareContext.get(context) == null) {
        // process sql only in the outermost prepare call and save the original sql in context
        String processSql = JdbcSingletons.processSql(connection, sql, true);
        Scope scope = PrepareContext.init(context, sql).makeCurrent();
        return new Object[] {processSql, scope};
      } else {
        return new Object[] {sql, null};
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void addDbInfo(
        @Advice.Return PreparedStatement statement,
        @Advice.Enter Object[] enterResult,
        @Advice.Thrown Throwable error) {
      Context context = Java8BytecodeBridge.currentContext();
      PrepareContext prepareContext = PrepareContext.get(context);
      Scope scope = (Scope) enterResult[1];
      if (scope != null) {
        scope.close();
      }
      if (error != null
          || prepareContext == null
          || JdbcSingletons.isWrapper(statement, PreparedStatement.class)) {
        return;
      }

      String originalSql = prepareContext.get();
      JdbcData.preparedStatement.set(statement, originalSql);
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
      if (JdbcSingletons.isWrapper(connection, Connection.class)) {
        return null;
      }

      return AdviceScope.start(connection, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
