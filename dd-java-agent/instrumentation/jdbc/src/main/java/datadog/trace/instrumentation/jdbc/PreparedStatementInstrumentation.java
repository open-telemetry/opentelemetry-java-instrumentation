package datadog.trace.instrumentation.jdbc;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

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
import java.sql.PreparedStatement;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class PreparedStatementInstrumentation extends Instrumenter.Configurable {

  public PreparedStatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(failSafe(isSubTypeOf(PreparedStatement.class))))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
                    PreparedStatementAdvice.class.getName()))
        .asDecorator();
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.This final PreparedStatement statement) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(PreparedStatement.class);
      if (callDepth > 0) {
        return null;
      }
      final String sql = JDBCMaps.preparedStatements.get(statement);
      final Connection connection;
      try {
        connection = statement.getConnection();
      } catch (final Throwable e) {
        // Had some problem getting the connection.
        return NoopScope.INSTANCE;
      }

      JDBCMaps.DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
      if (dbInfo == null) {
        dbInfo = JDBCMaps.DBInfo.DEFAULT;
      }
      final Scope scope =
          GlobalTracer.get().buildSpan(dbInfo.getType() + ".query").startActive(true);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, dbInfo.getType());
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, "java-jdbc-prepared_statement");

      span.setTag(DDTags.SERVICE_NAME, dbInfo.getType());
      span.setTag(DDTags.RESOURCE_NAME, sql == null ? JDBCMaps.DB_QUERY : sql);
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
        CallDepthThreadLocalMap.reset(PreparedStatement.class);
      }
    }
  }
}
