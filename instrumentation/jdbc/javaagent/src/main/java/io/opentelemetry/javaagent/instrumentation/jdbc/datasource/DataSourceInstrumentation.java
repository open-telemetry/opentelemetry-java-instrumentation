/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.datasource;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.dataSourceInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.sql.Connection;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DataSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.sql.DataSource"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getConnection").and(returns(implementsInterface(named("java.sql.Connection")))),
        DataSourceInstrumentation.class.getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {

    public static class AdviceScope {
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      private final CallDepth callDepth;

      private AdviceScope(CallDepth callDepth, @Nullable Context context, @Nullable Scope scope) {
        this.context = context;
        this.scope = scope;
        this.callDepth = callDepth;
      }

      @Nullable
      public static AdviceScope start(CallDepth callDepth, DataSource ds) {
        if (callDepth.getAndIncrement() < 0) {
          return new AdviceScope(callDepth, null, null);
        }

        Context parentContext = Context.current();
        if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
          // this instrumentation is already very noisy, and calls to getConnection outside of an
          // existing trace do not tend to be very interesting
          return new AdviceScope(callDepth, null, null);
        }

        if (!dataSourceInstrumenter().shouldStart(parentContext, ds)) {
          return new AdviceScope(callDepth, null, null);
        }

        Context context = dataSourceInstrumenter().start(parentContext, ds);
        return new AdviceScope(callDepth, context, context.makeCurrent());
      }

      public void end(
          DataSource ds, @Nullable Connection connection, @Nullable Throwable throwable) {
        if(callDepth.decrementAndGet() > 0||scope == null) {
          return;
        }

        scope.close();
        DbInfo dbInfo = null;
        Connection realConnection = JdbcUtils.unwrapConnection(connection);
        if (realConnection != null) {
          dbInfo = JdbcUtils.extractDbInfo(realConnection);
        }
        dataSourceInstrumenter().end(context, ds, dbInfo, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope start(@Advice.This DataSource ds) {
      return AdviceScope.start(CallDepth.forClass(DataSource.class),ds);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This DataSource ds,
        @Advice.Return @Nullable Connection connection,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter  AdviceScope adviceScope) {
        adviceScope.end(ds, connection, throwable);
    }
  }
}
