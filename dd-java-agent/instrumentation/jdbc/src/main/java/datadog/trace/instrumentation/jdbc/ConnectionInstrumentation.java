package datadog.trace.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.JDBCMaps;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ConnectionInstrumentation extends Instrumenter.Default {

  public ConnectionInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(isSubTypeOf(Connection.class));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        nameStartsWith("prepare")
            .and(takesArgument(0, String.class))
            .and(returns(PreparedStatement.class)),
        ConnectionPrepareAdvice.class.getName());
    return transformers;
  }

  public static class ConnectionPrepareAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(
        @Advice.Argument(0) final String sql, @Advice.Return final PreparedStatement statement) {
      JDBCMaps.preparedStatements.put(statement, sql);
    }
  }
}
