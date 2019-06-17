package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class DriverInstrumentation extends Instrumenter.Default {

  public DriverInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.sql.Driver")));
  }

  @Override
  public String[] helperClassNames() {
    final List<String> helpers = new ArrayList<>(JDBCConnectionUrlParser.values().length + 4);

    helpers.add(packageName + ".DBInfo");
    helpers.add(packageName + ".DBInfo$Builder");
    helpers.add(packageName + ".JDBCMaps");
    helpers.add(packageName + ".JDBCConnectionUrlParser");

    for (final JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      helpers.add(parser.getClass().getName());
    }
    return helpers.toArray(new String[0]);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("connect")
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, Properties.class))
            .and(returns(Connection.class)),
        DriverAdvice.class.getName());
  }

  public static class DriverAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(
        @Advice.Argument(0) final String url,
        @Advice.Argument(1) final Properties props,
        @Advice.Return final Connection connection) {
      final DBInfo dbInfo = JDBCConnectionUrlParser.parse(url, props);
      JDBCMaps.connectionInfo.put(connection, dbInfo);
    }
  }
}
