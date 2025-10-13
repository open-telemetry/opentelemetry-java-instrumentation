/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
    transformer.applyAdviceToMethod(
        named("addBatch").and(takesArgument(0, String.class)).and(isPublic()),
        StatementInstrumentation.class.getName() + "$AddBatchAdvice");
    transformer.applyAdviceToMethod(
        named("clearBatch").and(isPublic()),
        StatementInstrumentation.class.getName() + "$ClearBatchAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("executeBatch", "executeLargeBatch").and(takesNoArguments()).and(isPublic()),
        StatementInstrumentation.class.getName() + "$ExecuteBatchAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(isPublic()).and(takesNoArguments()),
        StatementInstrumentation.class.getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class StatementAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) String sql, @Advice.This Statement statement) {
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return new Object[] {null, sql};
      }

      String processedSql = JdbcSingletons.processSql(sql);
      return new Object[] {
        JdbcAdviceScope.startStatement(CallDepth.forClass(Statement.class), sql, statement),
        processedSql
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      JdbcAdviceScope adviceScope = (JdbcAdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AddBatchAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static String addBatch(
        @Advice.This Statement statement, @Advice.Argument(0) String sql) {
      if (statement instanceof PreparedStatement) {
        return sql;
      }
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return sql;
      }

      JdbcData.addStatementBatch(statement, sql);
      return JdbcSingletons.processSql(sql);
    }
  }

  @SuppressWarnings("unused")
  public static class ClearBatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void clearBatch(@Advice.This Statement statement) {
      JdbcData.clearBatch(statement);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteBatchAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static JdbcAdviceScope onEnter(@Advice.This Statement statement) {
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return null;
      }

      return JdbcAdviceScope.startBatch(CallDepth.forClass(Statement.class), statement);
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
  public static class CloseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void closeStatement(@Advice.This Statement statement) {
      JdbcData.close(statement);
    }
  }
}
