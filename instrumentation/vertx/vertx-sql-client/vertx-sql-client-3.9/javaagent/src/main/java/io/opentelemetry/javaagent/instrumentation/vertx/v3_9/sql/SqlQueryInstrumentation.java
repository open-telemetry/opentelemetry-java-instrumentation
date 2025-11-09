/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Vertx;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SqlQueryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.SqlClientBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("query").and(takesArgument(0, String.class)), 
        SqlQueryInstrumentation.class.getName() + "$QueryAdvice");

    transformer.applyAdviceToMethod(
        named("preparedQuery").and(takesArgument(0, String.class)),
        SqlQueryInstrumentation.class.getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(@Advice.Argument(0) String sql) {
      
      // Filter out internal MySQL queries
      if (sql != null && (sql.toLowerCase(java.util.Locale.ROOT).contains("show variables") || 
                          sql.toLowerCase(java.util.Locale.ROOT).contains("select @@") ||
                          sql.toLowerCase(java.util.Locale.ROOT).startsWith("/* ping */"))) {
        return null; // Skip instrumentation for these queries
      }
      
      Tracer tracer = GlobalOpenTelemetry.get().getTracer("vertx-sql-client");
      String spanName = sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
      
      // Try to get context from Vert.x context first
      Context parentContext = Context.current();
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      
      if (vertxContext != null
          && (parentContext==null||parentContext==Context.root())
      ) {
        // Check if there's a stored OpenTelemetry context in Vert.x context
        Context storedContext =
//            null;
        vertxContext.get("otel.context");
//        System.out.println("DEBUG: Vert.x context found, stored OTel context: " + storedContext);
        if (storedContext != null) {
          parentContext = storedContext;
//          System.out.println("DEBUG: Using stored context as parent: " + parentContext);
        } else {
//          System.out.println("DEBUG: No OTel context stored in Vert.x context");
        }
      } else {
//        System.out.println("DEBUG: No Vert.x context available");
      }
      
      Span span = tracer.spanBuilder(spanName)
          .setParent(parentContext)
          .setAttribute("db.statement", sql)
          .setAttribute("db.system", "mysql")
          .setAttribute("db.operation", extractOperation(sql))
          .startSpan();
      
//      System.out.println("SQL query executed: " + sql + " (context from Vert.x: " + (vertxContext != null) + ")");
      return new Object[]{span, parentContext.makeCurrent()};
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter Object[] state, @Advice.Thrown Throwable throwable) {
      if (state != null && state.length > 1) {
        Span span = (Span) state[0];
        Scope scope = (Scope) state[1];
        
        if (throwable != null && span != null) {
          span.recordException(throwable);
        }
        if (span != null) {
          span.end();
        }
        if (scope != null) {
          scope.close();
        }
      }
    }
    
    public static String extractOperation(String sql) {
      if (sql == null) {
        return "unknown";
      }
      String trimmed = sql.trim().toUpperCase(java.util.Locale.ROOT);
      if (trimmed.startsWith("SELECT")) {
        return "SELECT";
      }
      if (trimmed.startsWith("INSERT")) {
        return "INSERT";
      }
      if (trimmed.startsWith("UPDATE")) {
        return "UPDATE";
      }
      if (trimmed.startsWith("DELETE")) {
        return "DELETE";
      }
      return "OTHER";
    }
  }
}
