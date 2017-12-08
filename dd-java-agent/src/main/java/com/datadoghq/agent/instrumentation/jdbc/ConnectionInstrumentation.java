package com.datadoghq.agent.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import dd.trace.DDAdvice;
import dd.trace.Instrumenter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.WeakHashMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class ConnectionInstrumentation implements Instrumenter {
  public static final Map<PreparedStatement, String> preparedStatements = new WeakHashMap<>();

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named(Connection.class.getName()))))
        .transform(
            DDAdvice.create()
                .advice(
                    nameStartsWith("prepare")
                        .and(takesArgument(0, String.class))
                        .and(returns(PreparedStatement.class)),
                    ConnectionAdvice.class.getName()))
        .asDecorator();
  }

  public static class ConnectionAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(
        @Advice.Argument(0) final String sql, @Advice.Return final PreparedStatement statement) {
      preparedStatements.put(statement, sql);
    }
  }
}
