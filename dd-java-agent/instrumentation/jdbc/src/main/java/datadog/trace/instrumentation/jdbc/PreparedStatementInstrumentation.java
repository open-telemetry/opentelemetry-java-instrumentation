package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jdbc.JDBCDecorator.DECORATE;
import static datadog.trace.instrumentation.jdbc.JDBCUtils.connectionFromStatement;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.util.GlobalTracer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class PreparedStatementInstrumentation extends Instrumenter.Default {

  public PreparedStatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.sql.PreparedStatement")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      packageName + ".JDBCDecorator",
      packageName + ".JDBCMaps",
      packageName + ".JDBCMaps$DBInfo",
      packageName + ".JDBCUtils",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
        PreparedStatementAdvice.class.getName());
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.This final PreparedStatement statement) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(PreparedStatement.class);
      if (callDepth > 0) {
        return null;
      }

      final Connection connection = connectionFromStatement(statement);
      if (connection == null) {
        return NoopScopeManager.NoopScope.INSTANCE;
      }

      final Scope scope = GlobalTracer.get().buildSpan("database.query").startActive(true);
      final Span span = scope.span();
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, connection);
      DECORATE.onPreparedStatement(span, statement);
      span.setTag("span.origin.type", statement.getClass().getName());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        DECORATE.onError(scope.span(), throwable);
        DECORATE.beforeFinish(scope.span());
        scope.close();
        CallDepthThreadLocalMap.reset(PreparedStatement.class);
      }
    }
  }
}
