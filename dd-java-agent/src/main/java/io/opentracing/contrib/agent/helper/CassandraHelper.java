package io.opentracing.contrib.agent.helper;

import com.datastax.driver.core.Session;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;


public class CassandraHelper extends Helper {

	protected CassandraHelper(Rule rule) {
		super(rule);
	}

	/**
	 *
	 * @param session
	 * @return
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws InstantiationException
	 */
	public Session patch(Object session) throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException {

		Class<?> clazz = Class.forName("io.opentracing.contrib.cassandra.TracingSession");
		Constructor<?> constructor = clazz.getDeclaredConstructor(Session.class);
		constructor.setAccessible(true);
		return (Session) constructor.newInstance(session);

	}
}