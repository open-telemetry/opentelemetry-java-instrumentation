/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.statementInstrumenter;
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
import io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConnectionInstrumentation implements TypeInstrumentation {

  private static final boolean TXN_ENABLED =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.jdbc.experimental.txn.enabled", false);

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
    if (TXN_ENABLED) {
      transformer.applyAdviceToMethod(
          namedOneOf("commit", "rollback").and(takesNoArguments()).and(isPublic()),
          ConnectionInstrumentation.class.getName() + "$TxnAdvice");
    }
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
  public static class TxnAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      DbInfo dbInfo = null;
      Connection realConnection = JdbcUtils.unwrapConnection(connection);
      if (realConnection != null) {
        dbInfo = JdbcUtils.extractDbInfo(realConnection);
      }
      if (dbInfo == null) {
        return;
      }
      DbRequest request = DbRequest.create(dbInfo, methodName.toUpperCase(Locale.ROOT));

      if (!statementInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      request = DbRequest.create(request.getDbInfo(), methodName.toUpperCase(Locale.ROOT));
      context = statementInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") DbRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      statementInstrumenter().end(context, request, null, throwable);
    }
  }
}
