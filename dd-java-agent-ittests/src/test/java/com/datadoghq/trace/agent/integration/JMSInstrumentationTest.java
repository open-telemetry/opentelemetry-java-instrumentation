package com.datadoghq.trace.agent.integration;

import io.opentracing.contrib.jms.TracingMessageProducer;
import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.jms.*;

import static org.assertj.core.api.Assertions.assertThat;

public class JMSInstrumentationTest {


	@ClassRule
	public static EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker();
	private static Session session;
	private static ActiveMQQueue destination;

	@BeforeClass
	public static void start() throws JMSException {

		broker.start();
		ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory();

		destination = new ActiveMQQueue("someQueue");
		Connection connection = connectionFactory.createConnection();
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

	}

	@Test
	public void test() throws Exception {


		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);

		assertThat(producer).isInstanceOf(TracingMessageProducer.class);
		assertThat(consumer).isInstanceOf(TracingMessageConsumer.class);
	}

}
