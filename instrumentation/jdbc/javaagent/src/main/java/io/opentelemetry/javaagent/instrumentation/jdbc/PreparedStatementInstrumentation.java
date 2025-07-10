/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.CAPTURE_QUERY_PARAMETERS;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.statementInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.URL;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.RowId;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PreparedStatementInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.PreparedStatement");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.PreparedStatement"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("execute")
            .and(not(namedOneOf("executeBatch", "executeLargeBatch")))
            .and(takesArguments(0))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$PreparedStatementAdvice");
    transformer.applyAdviceToMethod(
        named("addBatch").and(takesNoArguments()).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$AddBatchAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf(
                "setBoolean",
                "setShort",
                "setInt",
                "setLong",
                "setFloat",
                "setDouble",
                "setBigDecimal",
                "setString",
                "setDate",
                "setTime",
                "setTimestamp",
                "setURL",
                "setRowId",
                "setNString",
                "setObject")
            .and(takesArgument(0, int.class))
            .and(takesArguments(2))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetParameter2Advice");
    transformer.applyAdviceToMethod(
        namedOneOf("setDate", "setTime", "setTimestamp")
            .and(takesArgument(0, int.class))
            .and(takesArgument(2, Calendar.class))
            .and(takesArguments(3))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetTimeParameter3Advice");
    transformer.applyAdviceToMethod(
        namedOneOf("setObject")
            .and(takesArgument(0, int.class))
            .and(takesArgument(2, int.class))
            .and(takesArguments(3))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetParameter3Advice");
    transformer.applyAdviceToMethod(
        named("clearParameters").and(takesNoArguments()).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$ClearParametersAdvice");
  }

  @SuppressWarnings("unused")
  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This PreparedStatement statement,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") DbRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      // skip prepared statements without attached sql, probably a wrapper around the actual
      // prepared statement
      if (JdbcData.preparedStatement.get(statement) == null) {
        return;
      }

      // Connection#getMetaData() may execute a Statement or PreparedStatement to retrieve DB info
      // this happens before the DB CLIENT span is started (and put in the current context), so this
      // instrumentation runs again and the shouldStartSpan() check always returns true - and so on
      // until we get a StackOverflowError
      // using CallDepth prevents this, because this check happens before Connection#getMetadata()
      // is called - the first recursive Statement call is just skipped and we do not create a span
      // for it
      callDepth = CallDepth.forClass(Statement.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = currentContext();
      Map<String, String> parameters = JdbcData.getParameters(statement);
      request = DbRequest.create(statement, parameters);

      if (request == null || !statementInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = statementInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") DbRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth == null || callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        statementInstrumenter().end(context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AddBatchAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addBatch(@Advice.This PreparedStatement statement) {
      JdbcData.addPreparedStatementBatch(statement);
    }
  }

  @SuppressWarnings("unused")
  public static class SetParameter2Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Object value) {
      if (!CAPTURE_QUERY_PARAMETERS) {
        return;
      }

      String str = null;

      if (value instanceof Boolean
          // Short, Int, Long, Float, Double, BigDecimal
          || value instanceof Number
          || value instanceof String
          || value instanceof Date
          || value instanceof Time
          || value instanceof Timestamp
          || value instanceof URL
          || value instanceof RowId) {
        str = value.toString();
      }

      if (str != null) {
        JdbcData.addParameter(statement, Integer.toString(index - 1), str);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetParameter3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Object value,
        @Advice.Argument(2) int targetSqlType) {
      if (!CAPTURE_QUERY_PARAMETERS) {
        return;
      }

      String str = null;

      if (value instanceof Boolean
          // Short, Int, Long, Float, Double, BigDecimal
          || value instanceof Number
          || value instanceof String
          || value instanceof Date
          || value instanceof Time
          || value instanceof Timestamp
          || value instanceof URL
          || value instanceof RowId) {
        str = value.toString();
      }

      if (str != null) {
        JdbcData.addParameter(statement, Integer.toString(index - 1), str);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetTimeParameter3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Object value,
        @Advice.Argument(2) Calendar calendar) {
      if (!CAPTURE_QUERY_PARAMETERS) {
        return;
      }

      String str = null;

      if (value instanceof Date || value instanceof Time || value instanceof Timestamp) {
        str = value.toString();
      }

      if (str != null) {
        JdbcData.addParameter(statement, Integer.toString(index - 1), str);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ClearParametersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void clearBatch(@Advice.This PreparedStatement statement) {
      JdbcData.clearParameters(statement);
    }
  }
}
