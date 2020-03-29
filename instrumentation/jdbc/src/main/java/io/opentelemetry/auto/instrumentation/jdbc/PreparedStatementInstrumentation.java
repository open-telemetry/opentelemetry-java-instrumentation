/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.instrumentation.jdbc.JDBCDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jdbc.JDBCDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jdbc.JDBCUtils.connectionFromStatement;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class PreparedStatementInstrumentation extends Instrumenter.Default {

  public PreparedStatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.PreparedStatement"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JDBCMaps", packageName + ".JDBCUtils", packageName + ".JDBCDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
        PreparedStatementInstrumentation.class.getName() + "$PreparedStatementAdvice");
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(@Advice.This final PreparedStatement statement) {
      final Connection connection = connectionFromStatement(statement);
      if (connection == null) {
        return null;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(PreparedStatement.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span =
          TRACER
              .spanBuilder(DECORATE.spanNameOnPreparedStatement(statement))
              .setSpanKind(CLIENT)
              .startSpan();
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, connection);
      DECORATE.onPreparedStatement(span, statement);
      span.setAttribute("span.origin.type", statement.getClass().getName());
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(PreparedStatement.class);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
