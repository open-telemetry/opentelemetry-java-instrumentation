package com.datadoghq.agent.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datadoghq.trace.DDTags;
import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
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
                    nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
                    PreparedStatementAdvice.class.getName()))
        .asDecorator();
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter
    public static ActiveSpan startSpan(@Advice.This final PreparedStatement statement) {
      final String sql = ConnectionInstrumentation.preparedStatements.get(statement);
      final Connection connection;
      try {
        connection = statement.getConnection();
      } catch (final Throwable e) {
        // Had some problem getting the connection.
        return NoopActiveSpanSource.NoopActiveSpan.INSTANCE;
      }

      final DriverInstrumentation.DBInfo dbInfo =
          DriverInstrumentation.connectionInfo.get(connection);

      final ActiveSpan span =
          GlobalTracer.get().buildSpan(dbInfo.getType() + ".query").startActive();
      Tags.DB_TYPE.set(span, dbInfo.getType());
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, "java-jdbc-prepared_statement");

      span.setTag(DDTags.SERVICE_NAME, dbInfo.getType());
      span.setTag(DDTags.RESOURCE_NAME, sql);
      span.setTag(DDTags.SPAN_TYPE, "sql");
      span.setTag("span.origin.type", statement.getClass().getName());
      span.setTag("db.jdbc.url", dbInfo.getUrl());

      if (dbInfo.getUser() != null) {
        Tags.DB_USER.set(span, dbInfo.getUser());
      }
      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final ActiveSpan activeSpan, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        Tags.ERROR.set(activeSpan, true);
        activeSpan.log(Collections.singletonMap("error.object", throwable));
      }
      activeSpan.deactivate();
    }
  }
}
