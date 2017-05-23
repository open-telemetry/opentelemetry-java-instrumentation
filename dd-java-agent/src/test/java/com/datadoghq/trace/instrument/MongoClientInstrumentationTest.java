package com.datadoghq.trace.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.mongodb.MongoClient;

import io.opentracing.contrib.mongo.TracingCommandListener;


public class MongoClientInstrumentationTest {
	
	@Test
	public void test() {
		MongoClient mongoClient = new MongoClient();
		
		assertThat(mongoClient.getMongoClientOptions().getCommandListeners().size()).isEqualTo(1);
		assertThat(mongoClient.getMongoClientOptions().getCommandListeners().get(0).getClass()).isEqualTo(TracingCommandListener.class);
		
		mongoClient.close();
	}
}
