//package com.datadoghq.trace.instrument;
//
//import com.datastax.driver.core.Cluster;
//import com.datastax.driver.core.Session;
//import org.apache.cassandra.exceptions.ConfigurationException;
//import org.apache.thrift.transport.TTransportException;
//import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.io.IOException;
//import java.util.concurrent.ExecutionException;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * Created by gpolaert on 6/2/17.
// */
//public class CassandraIntegrationTest {
//
//
//	@Before
//	public void start() throws InterruptedException, TTransportException, ConfigurationException, IOException {
//		EmbeddedCassandraServerHelper.startEmbeddedCassandra(20000L);
//	}
//
//	@After
//	public void stop() {
//		EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
//	}
//
//
//	@Test
//	public void testNewSessionSync() throws ClassNotFoundException {
//		Cluster cluster = EmbeddedCassandraServerHelper.getCluster();
//		Session session = cluster.newSession();
//		assertThat(session).isInstanceOf(Class.forName("io.opentracing.contrib.cassandra.TracingSession"));
//
//
//	}
//
//	@Test
//	public void testNewSessionAsync() throws ClassNotFoundException, ExecutionException, InterruptedException {
//		Cluster cluster = EmbeddedCassandraServerHelper.getCluster();
//		Session session = cluster.connectAsync().get();
//		assertThat(session).isInstanceOf(Class.forName("io.opentracing.contrib.cassandra.TracingSession"));
//
//
//	}
//}
