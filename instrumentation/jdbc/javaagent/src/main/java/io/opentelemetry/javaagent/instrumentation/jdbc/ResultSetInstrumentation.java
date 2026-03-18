/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.ResultSet;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResultSetInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.sql.ResultSet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.ResultSet"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("next").and(takesNoArguments()).and(isPublic()),
        ResultSetInstrumentation.class.getName() + "$ResultSetNextAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(takesNoArguments()).and(isPublic()),
        ResultSetInstrumentation.class.getName() + "$ResultSetCloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResultSetNextAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ResultSetInfo onEnter(@Advice.This ResultSet resultSet) {
      // Skip wrappers - we only track spans on the underlying driver ResultSet
      if (JdbcSingletons.isWrapper(resultSet, ResultSet.class)) {
        return null;
      }
      return JdbcSingletons.resultSetInfo.get(resultSet);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return boolean hasNext, @Advice.Enter @Nullable ResultSetInfo info) {
      if (hasNext && info != null) {
        JdbcAdviceScope.incrementResultSetRowCount(info);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResultSetCloseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This ResultSet resultSet) {
      // Skip wrappers - close() on a wrapper will delegate to the underlying driver ResultSet,
      // which will trigger this advice again on the real instance
      if (JdbcSingletons.isWrapper(resultSet, ResultSet.class)) {
        return;
      }
      JdbcAdviceScope.endSpanForResultSet(resultSet);
    }
  }
}
