package io.opentracing.contrib.agent.helper;

import com.datastax.driver.core.Session;
import io.opentracing.Tracer;
import io.opentracing.contrib.agent.OpenTracingHelper;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import java.lang.reflect.Constructor;


public class CassandraHelper extends Helper {

	private final OpenTracingHelper otHelper;
	private static final String LOG_PREFIX = "OTARULES - Cassandra contrib - ";

	protected CassandraHelper(Rule rule) {
		super(rule);
		otHelper = new OpenTracingHelper(rule);
	}

	/**
	 * @param session The session to be patched
	 */
	public Session patch(Session session) {


		debug(LOG_PREFIX + "Try to patch the session");

		try {

			Tracer tracer = otHelper.getTracer();
			Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
			Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class, Tracer.class);
			constructor.setAccessible(true);
			Object newSession = constructor.newInstance(session, tracer);
			debug(LOG_PREFIX + "Session patched");
			return (Session) newSession;

		} catch (Exception e) {
			err(LOG_PREFIX + "Session not patched, " + e.getMessage());
			errTraceException(e);
		}

		return session;



	}


}