package com.datadoghq.agent.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datadoghq.agent.instrumentation.Instrumenter;
import com.google.auto.service.AutoService;
import io.opentracing.ActiveSpan;
import io.opentracing.NoopActiveSpanSource;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class PreparedStatementInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named(PreparedStatement.class.getName()))))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    nameStartsWith("execute").and(takesArguments(0)),
                    PreparedStatementAdvice.class.getName()))
        .asDecorator();
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter
    public static ActiveSpan startSpan(@Advice.This final PreparedStatement statement) {
      // TODO: Should this happen always instead of just inside an existing tracer?
      if (GlobalTracer.get().activeSpan() == null) {
        return NoopActiveSpanSource.NoopActiveSpan.INSTANCE;
      }

      final String sql = ConnectionInstrumentation.preparedStatements.get(statement);

      final ActiveSpan span = GlobalTracer.get().buildSpan("sql.prepared_statement").startActive();
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, "java-jdbc");
      Tags.DB_STATEMENT.set(span, sql);
      span.setTag("span.origin.type", statement.getClass().getName());

      try {
        final Connection connection = statement.getConnection();
        final DriverInstrumentation.DBInfo dbInfo =
            DriverInstrumentation.connectionInfo.get(connection);

        span.setTag("db.jdbc.url", dbInfo.getUrl());
        span.setTag("db.schema", connection.getSchema());

        Tags.DB_TYPE.set(span, dbInfo.getType());
        if (dbInfo.getUser() != null) {
          Tags.DB_USER.set(span, dbInfo.getUser());
        }
      } finally {
        return span;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final ActiveSpan activeSpan,
        @Advice.Thrown(readOnly = false) final Throwable throwable) {
      if (throwable != null) {
        Tags.ERROR.set(activeSpan, true);
        activeSpan.log(Collections.singletonMap("error.object", throwable));
      }
      activeSpan.deactivate();
    }
  }
}
