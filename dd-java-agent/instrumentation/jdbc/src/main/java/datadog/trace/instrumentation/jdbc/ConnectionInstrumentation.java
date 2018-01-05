package datadog.trace.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.Data;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class ConnectionInstrumentation implements Instrumenter {
  public static final Map<Connection, DBInfo> connectionInfo = new WeakHashMap<>();
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
                    ConnectionPrepareAdvice.class.getName()))
        .transform(
            DDAdvice.create().advice(isConstructor(), ConnectionConstructorAdvice.class.getName()))
        .asDecorator();
  }

  public static class ConnectionPrepareAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(
        @Advice.Argument(0) final String sql, @Advice.Return final PreparedStatement statement) {
      preparedStatements.put(statement, sql);
    }
  }

  public static class ConnectionConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(@Advice.This final Connection connection) {
      try {
        final String url = connection.getMetaData().getURL();
        if (url != null) {
          // Remove end of url to prevent passwords from leaking:
          final String sanitizedURL = url.replaceAll("[?;].*", "");
          final String type = url.split(":")[1];
          String user = connection.getMetaData().getUserName();
          if (user != null && user.trim().equals("")) {
            user = null;
          }
          connectionInfo.put(connection, new DBInfo(sanitizedURL, type, user));
        }
      } catch (final Throwable t) {
        // object may not be fully initialized.
        // calling constructor will populate map
      }
    }
  }

  @Data
  public static class DBInfo {
    public static DBInfo UNKNOWN = new DBInfo("null", "unknown", null);
    private final String url;
    private final String type;
    private final String user;
  }
}
