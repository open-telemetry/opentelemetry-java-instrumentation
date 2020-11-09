/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.jdbc.DataSourceTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class JdbcDataSourceInstrumentationModule extends InstrumentationModule {
  public JdbcDataSourceInstrumentationModule() {
    super("jdbc-datasource");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".DataSourceTracer",
      packageName + ".DBInfo",
      packageName + ".DBInfo$Builder",
      packageName + ".JDBCConnectionUrlParser",
      packageName + ".JDBCConnectionUrlParser$1",
      packageName + ".JDBCConnectionUrlParser$2",
      packageName + ".JDBCConnectionUrlParser$3",
      packageName + ".JDBCConnectionUrlParser$4",
      packageName + ".JDBCConnectionUrlParser$5",
      packageName + ".JDBCConnectionUrlParser$6",
      packageName + ".JDBCConnectionUrlParser$7",
      packageName + ".JDBCConnectionUrlParser$8",
      packageName + ".JDBCConnectionUrlParser$9",
      packageName + ".JDBCConnectionUrlParser$10",
      packageName + ".JDBCConnectionUrlParser$11",
      packageName + ".JDBCConnectionUrlParser$12",
      packageName + ".JDBCConnectionUrlParser$13",
      packageName + ".JDBCConnectionUrlParser$14",
      packageName + ".JDBCConnectionUrlParser$15",
      packageName + ".JDBCConnectionUrlParser$16",
      packageName + ".JDBCConnectionUrlParser$17",
      packageName + ".JDBCMaps",
      packageName + ".JDBCUtils",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DataSourceInstrumentation());
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  private static final class DataSourceInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("javax.sql.DataSource"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(named("getConnection"), GetConnectionAdvice.class.getName());
    }
  }

  public static class GetConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(
        @Advice.This DataSource ds,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      // TODO this is very strange condition
      if (!Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
        // Don't want to generate a new top-level span
        return;
      }

      span = tracer().startSpan(ds.getClass().getSimpleName() + ".getConnection", CLIENT);
      scope = span.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span);
      }
    }
  }
}
