package com.datadoghq.trace.agent.integration;

import com.datastax.driver.core.Session;
import io.opentracing.Tracer;
import java.lang.reflect.Constructor;
import org.jboss.byteman.rule.Rule;

/** Patch each new sessions created when trying to connect to a Cassandra cluster. */
public class CassandraHelper extends DDAgentTracingHelper<Session> {

  protected CassandraHelper(Rule rule) {
    super(rule);
  }

  @Override
  public Session patch(Session session) {
    return super.patch(session);
  }

  /**
   * Strategy: each time we build a connection to a Cassandra cluster, the
   * com.datastax.driver.core.Cluster$Manager.newSession() method is called. The opentracing
   * contribution is a simple wrapper, so we just have to wrap the new session.
   *
   * @param session The fresh session to patch
   * @return A new tracing session
   * @throws Exception
   */
  protected Session doPatch(Session session) throws Exception {

    if ("io.opentracing.contrib.cassandra.TracingSession"
        .equals(session.getClass().getCanonicalName())) {
      return session;
    }

    Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
    Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class, Tracer.class);
    constructor.setAccessible(true);
    Session newSession = (Session) constructor.newInstance(session, tracer);

    return newSession;
  }
}
