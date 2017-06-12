package com.datadoghq.trace.instrument;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by gpolaert on 6/2/17.
 */
public class CassandraIntegrationTest {


	@Test
	public void testNewSessionSync() throws ClassNotFoundException {
		Cluster cluster = new Cluster.Builder().addContactPoint("").build();
		Session session = cluster.newSession();
		assertThat(session).isInstanceOf(Class.forName("io.opentracing.contrib.cassandra.TracingSession"));


	}

	@Test
	public void testNewSessionAsync() throws ClassNotFoundException {


		Cluster cluster = spy(new Cluster.Builder().addContactPoint("").build());
		doReturn(cluster).when(cluster).init();
		ListenableFuture<Session> session = null;
		try {
			session = cluster.connectAsync();
		} catch (Exception e) {

		}

		// find a way to test
//		assertThat(session).isInstanceOf(Class.forName("io.opentracing.contrib.cassandra.TracingSession"));


	}
}
