package datadog.trace.instrumentation.datastax.cassandra;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.driver.core.Session;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Constructor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class CassandraClientInstrumentation extends Instrumenter.Configurable {

  public CassandraClientInstrumentation() {
    super("cassandra");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("com.datastax.driver.core.Cluster$Manager"),
            classLoaderHasClasses(
                "com.datastax.driver.core.BoundStatement",
                "com.datastax.driver.core.BoundStatement",
                "com.datastax.driver.core.CloseFuture",
                "com.datastax.driver.core.Cluster",
                "com.datastax.driver.core.Host",
                "com.datastax.driver.core.PreparedStatement",
                "com.datastax.driver.core.RegularStatement",
                "com.datastax.driver.core.ResultSet",
                "com.datastax.driver.core.ResultSetFuture",
                "com.datastax.driver.core.Session",
                "com.datastax.driver.core.Statement",
                "com.google.common.base.Function",
                "com.google.common.util.concurrent.Futures",
                "com.google.common.util.concurrent.ListenableFuture"))
        .transform(
            new HelperInjector(
                "io.opentracing.contrib.cassandra.TracingSession",
                "io.opentracing.contrib.cassandra.TracingSession$1",
                "io.opentracing.contrib.cassandra.TracingSession$2",
                "io.opentracing.contrib.cassandra.TracingCluster",
                "io.opentracing.contrib.cassandra.TracingCluster$1"))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(isPrivate()).and(named("newSession")).and(takesArguments(0)),
                    CassandraClientAdvice.class.getName()))
        .asDecorator();
  }

  public static class CassandraClientAdvice {
    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.driver.core.Cluster$Manager.newSession() method is called. The opentracing
     * contribution is a simple wrapper, so we just have to wrap the new session.
     *
     * @param session The fresh session to patch
     * @return A new tracing session
     * @throws Exception
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void injectTracingSession(@Advice.Return(readOnly = false) Session session)
        throws Exception {
      if (session.getClass().getName().endsWith("contrib.cassandra.TracingSession")) {
        return;
      }

      final Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
      final Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class, Tracer.class);
      constructor.setAccessible(true);
      session = (Session) constructor.newInstance(session, GlobalTracer.get());
    }
  }
}
