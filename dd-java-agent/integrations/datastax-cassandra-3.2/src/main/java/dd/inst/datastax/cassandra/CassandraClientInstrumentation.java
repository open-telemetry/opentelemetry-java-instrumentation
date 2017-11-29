package dd.inst.datastax.cassandra;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static dd.trace.ExceptionHandlers.defaultExceptionHandler;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.datastax.driver.core.Session;
import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Constructor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class CassandraClientInstrumentation implements Instrumenter {
  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
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
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    isMethod().and(isPrivate()).and(named("newSession")).and(takesArguments(0)),
                    CassandraClientAdvice.class.getName())
                .withExceptionHandler(defaultExceptionHandler()))
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

      Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
      Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class, Tracer.class);
      constructor.setAccessible(true);
      session = (Session) constructor.newInstance(session, GlobalTracer.get());
    }
  }
}
