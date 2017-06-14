package com.datadoghq.trace.instrument;

import org.junit.Before;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.writer.ListWriter;

import io.opentracing.util.GlobalTracer;

public class AAgentIntegration {

	protected static ListWriter writer = new ListWriter();
	protected static DDTracer tracer = new DDTracer(writer);

	@Before
	public void beforeTest() throws Exception {
		try{
			GlobalTracer.register(tracer);
		}catch(Exception e){
			//DO NOTHING IF ALREADY REGISTERED
		}
		writer.start();
	}
	
}
