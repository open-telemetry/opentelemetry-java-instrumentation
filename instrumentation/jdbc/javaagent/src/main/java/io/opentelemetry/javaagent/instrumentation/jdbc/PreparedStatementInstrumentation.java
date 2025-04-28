/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcPreparedStatementStringifier.stringifyNullParameter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcPreparedStatementStringifier.stringifyParameter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
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
import java.math.BigDecimal;
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
        named("setNull").and(takesArgument(0, int.class)).and(takesArguments(2)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetNull2Advice");
    transformer.applyAdviceToMethod(
        named("setNull").and(takesArgument(0, int.class)).and(takesArguments(3)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetNull3Advice");
    transformer.applyAdviceToMethod(
        named("setBoolean").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetBooleanAdvice");
    transformer.applyAdviceToMethod(
        named("setByte").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetByteAdvice");
    transformer.applyAdviceToMethod(
        named("setShort").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetShortAdvice");
    transformer.applyAdviceToMethod(
        named("setInt").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetIntAdvice");
    transformer.applyAdviceToMethod(
        named("setLong").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetLongAdvice");
    transformer.applyAdviceToMethod(
        named("setFloat").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetFloatAdvice");
    transformer.applyAdviceToMethod(
        named("setDouble").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetDoubleAdvice");
    transformer.applyAdviceToMethod(
        named("setBigDecimal").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetBigDecimalAdvice");
    transformer.applyAdviceToMethod(
        named("setString").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetStringAdvice");
    transformer.applyAdviceToMethod(
        named("setBytes").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetBytesAdvice");
    transformer.applyAdviceToMethod(
        named("setDate").and(takesArgument(0, int.class)).and(takesArguments(2)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetDate2Advice");
    transformer.applyAdviceToMethod(
        named("setDate").and(takesArgument(0, int.class)).and(takesArguments(3)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetDate3Advice");
    transformer.applyAdviceToMethod(
        named("setTime").and(takesArgument(0, int.class)).and(takesArguments(2)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetTime2Advice");
    transformer.applyAdviceToMethod(
        named("setTime").and(takesArgument(0, int.class)).and(takesArguments(3)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetTime3Advice");
    transformer.applyAdviceToMethod(
        named("setTimestamp")
            .and(takesArgument(0, int.class))
            .and(takesArguments(2))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetTimestamp2Advice");
    transformer.applyAdviceToMethod(
        named("setTimestamp")
            .and(takesArgument(0, int.class))
            .and(takesArguments(3))
            .and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetTimestamp3Advice");
    transformer.applyAdviceToMethod(
        named("setURL").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetUrlAdvice");
    transformer.applyAdviceToMethod(
        named("setRowId").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetRowIdAdvice");
    transformer.applyAdviceToMethod(
        named("setNString").and(takesArgument(0, int.class)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$SetNstringAdvice");
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
      Map<String, String> parameters = JdbcData.parameters.get(statement);
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
  public static class SetNull2Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) int sqlType) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyNullParameter());
    }
  }

  @SuppressWarnings("unused")
  public static class SetNull3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) int sqlType,
        @Advice.Argument(2) String typeName) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyNullParameter());
    }
  }

  @SuppressWarnings("unused")
  public static class SetBooleanAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) boolean value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetByteAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) byte value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetShortAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) short value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetIntAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) int value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetLongAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) long value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetFloatAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) float value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetDoubleAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) double value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetBigDecimalAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) BigDecimal value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetStringAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) String value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetBytesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) byte[] value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetDate2Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Date value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetDate3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Date value,
        @Advice.Argument(2) Calendar calendar) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetTime2Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Time value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetTime3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Time value,
        @Advice.Argument(2) Calendar calendar) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetTimestamp2Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Timestamp value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetTimestamp3Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) Timestamp value,
        @Advice.Argument(2) Calendar calendar) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings({"unused", "IdentifierName"})
  public static class SetUrlAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) URL value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetRowIdAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) RowId value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }

  @SuppressWarnings("unused")
  public static class SetNstringAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This PreparedStatement statement,
        @Advice.Argument(0) int index,
        @Advice.Argument(1) String value) {
      JdbcData.addParameter(statement, Integer.toString(index - 1), stringifyParameter(value));
    }
  }
}
