package com.datadoghq.trace.instrument;

import org.junit.Before;

import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

public class AAgentIntegration {

	protected static MockTracer tracer = new MockTracer();

	@Before
	public void beforeTest() throws Exception {
		try{
			GlobalTracer.register(tracer);
		}catch(Exception e){
			//DO NOTHING IF ALREADY REGISTERED
		}
		tracer.reset();
	}
	
}
