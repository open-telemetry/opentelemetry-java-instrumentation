package io.opentracing.contrib.agent.helper;

import com.datastax.driver.core.Session;
import io.opentracing.Tracer;
import org.jboss.byteman.rule.Rule;

import java.lang.reflect.Constructor;


public class CassandraHelperAgent extends DDAgentTracingHelper<Session> {


	protected CassandraHelperAgent(Rule rule) {
		super(rule);
	}


	public Session patch(Session session) {
		return super.patch(session);
	}

	@Override
	protected Session doPatch(Session session) throws Exception {


		Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
		Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class, Tracer.class);
		constructor.setAccessible(true);
		Session newSession = (Session) constructor.newInstance(session, tracer);

		return newSession;


	}


}