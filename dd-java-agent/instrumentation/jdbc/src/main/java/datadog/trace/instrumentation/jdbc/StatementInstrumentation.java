package datadog.trace.instrumentation.jdbc;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.JDBCMaps;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopScopeManager.NoopScope;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class StatementInstrumentation extends Instrumenter.Configurable {

  public StatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(failSafe(isSubTypeOf(Statement.class))))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
                    StatementAdvice.class.getName()))
        .asDecorator();
  }

  public static class StatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final String sql, @Advice.This final Statement statement) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
      if (callDepth > 0) {
        return null;
      }
      final Connection connection;
      try {
        connection = statement.getConnection();
      } catch (final Throwable e) {
        // Had some problem getting the connection.
        return NoopScope.INSTANCE;
      }

      JDBCMaps.DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
      /**
       * Logic to get the DBInfo from a JDBC Connection, if the connection was never seen before,
       * the connectionInfo map will return null and will attempt to extract DBInfo from the
       * connection. If the DBInfo can't be extracted, then the connection will be stored with the
       * DEFAULT DBInfo as the value in the connectionInfo map to avoid retry overhead.
       *
       * <p>This should be a util method to be shared between PreparedStatementInstrumentation and
       * StatementInstrumentation class, but java.sql.* are on the platform classloaders in Java 9,
       * which prevents us from referencing them in the bootstrap utils.
       */
      {
        if (dbInfo == null) {
          try {
            final String url = connection.getMetaData().getURL();
            if (url != null) {
              // Remove end of url to prevent passwords from leaking:
              final String sanitizedURL = url.replaceAll("[?;].*", "");
              final String type = url.split(":", -1)[1];
              String user = connection.getMetaData().getUserName();
              if (user != null && user.trim().equals("")) {
                user = null;
              }
              dbInfo = new JDBCMaps.DBInfo(sanitizedURL, type, user);
            } else {
              dbInfo = JDBCMaps.DBInfo.DEFAULT;
            }
          } catch (SQLException se) {
            dbInfo = JDBCMaps.DBInfo.DEFAULT;
          }
          JDBCMaps.connectionInfo.put(connection, dbInfo);
        }
      }

      final Scope scope =
          GlobalTracer.get().buildSpan(dbInfo.getType() + ".query").startActive(true);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, dbInfo.getType());
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, "java-jdbc-statement");

      span.setTag(DDTags.SERVICE_NAME, dbInfo.getType());
      span.setTag(DDTags.RESOURCE_NAME, sql);
      span.setTag(DDTags.SPAN_TYPE, "sql");
      span.setTag("span.origin.type", statement.getClass().getName());
      span.setTag("db.jdbc.url", dbInfo.getUrl());

      if (dbInfo.getUser() != null) {
        Tags.DB_USER.set(span, dbInfo.getUser());
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        if (throwable != null) {
          final Span span = scope.span();
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
        scope.close();
        CallDepthThreadLocalMap.reset(Statement.class);
      }
    }
  }
}
