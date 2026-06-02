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

class StatementInstrumentation implements TypeInstrumentation {

  private static final String[] NAMED_CLASSES =
      new String[] {"org.sqlite.jdbc3.JDBC3Statement", "org.sqlite.jdbc4.JDBC4Statement"};

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.Statement");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Statement")).or(namedOneOf(NAMED_CLASSES));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        getClass().getName() + "$StatementAdvice");
    transformer.applyAdviceToMethod(
        named("addBatch").and(takesArgument(0, String.class)).and(isPublic()),
        getClass().getName() + "$AddBatchAdvice");
    transformer.applyAdviceToMethod(
        named("clearBatch").and(isPublic()), getClass().getName() + "$ClearBatchAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("executeBatch", "executeLargeBatch").and(takesNoArguments()).and(isPublic()),
        getClass().getName() + "$ExecuteBatchAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(isPublic()).and(takesNoArguments()),
        getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class StatementAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(0) String sql, @Advice.This Object object) {
      if (!(object instanceof Statement)) {
        return new Object[] {null, sql};
      }
      Statement statement = (Statement) object;
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return new Object[] {null, sql};
      }

      String processedSql = JdbcSingletons.processSql(statement, sql, true);
      return new Object[] {
        JdbcAdviceScope.startStatement(CallDepth.forClass(Statement.class), sql, statement),
        processedSql
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
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
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static String addBatch(@Advice.This Object object, @Advice.Argument(0) String sql) {
      if (!(object instanceof Statement)) {
        return sql;
      }
      Statement statement = (Statement) object;
      if (statement instanceof PreparedStatement) {
        return sql;
      }

      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return sql;
      }

      JdbcData.addStatementBatch(statement, sql);
      return JdbcSingletons.processSql(statement, sql, true);
    }
  }

  @SuppressWarnings("unused")
  public static class ClearBatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void clearBatch(@Advice.This Object object) {
      if (object instanceof Statement) {
        Statement statement = (Statement) object;
        JdbcData.clearBatch(statement);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteBatchAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JdbcAdviceScope onEnter(@Advice.This Object object) {
      if (!(object instanceof Statement)) {
        return null;
      }
      Statement statement = (Statement) object;
      if (JdbcSingletons.isWrapper(statement, Statement.class)) {
        return null;
      }

      return JdbcAdviceScope.startBatch(CallDepth.forClass(Statement.class), statement);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void closeStatement(@Advice.This Object object) {
      if (object instanceof Statement) {
        Statement statement = (Statement) object;
        JdbcData.close(statement);
      }
    }
  }
}
