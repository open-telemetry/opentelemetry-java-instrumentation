/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.CAPTURE_QUERY_PARAMETERS;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

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
import javax.annotation.Nullable;
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
                "setByte",
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

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static JdbcAdviceScope onEnter(@Advice.This PreparedStatement statement) {
      // skip prepared statements without attached sql, probably a wrapper around the actual
      // prepared statement
      if (JdbcData.preparedStatement.get(statement) == null) {
        return null;
      }
      if (JdbcSingletons.isWrapper(statement, PreparedStatement.class)) {
        return null;
      }

      return JdbcAdviceScope.startPreparedStatement(CallDepth.forClass(Statement.class), statement);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable JdbcAdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AddBatchAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addBatch(@Advice.This PreparedStatement statement) {
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return;
      }

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
      if (JdbcSingletons.isWrapper(statement, PreparedStatement.class)) {
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
      if (JdbcSingletons.isWrapper(statement, PreparedStatement.class)) {
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
      if (JdbcSingletons.isWrapper(statement, PreparedStatement.class)) {
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
