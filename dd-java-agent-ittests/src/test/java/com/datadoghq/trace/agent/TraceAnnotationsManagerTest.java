package com.datadoghq.trace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.datadoghq.trace.agent.integration.AAgentIntegration;

public class TraceAnnotationsManagerTest extends AAgentIntegration{
	
	@Before
	public void beforeTest() throws Exception {
		super.beforeTest();
	}

	@Test
	public void testAnnotations() {
		//Test single span in new trace
		SayTracedHello.sayHello();
		
		assertThat(writer.firstTrace().size()).isEqualTo(1);
		assertThat(writer.firstTrace().get(0).getOperationName()).isEqualTo("SAY_HELLO");
		assertThat(writer.firstTrace().get(0).getServiceName()).isEqualTo("test");
		
		writer.start();

		//Test new trace with 2 children spans
		SayTracedHello.sayHELLOsayHA();
		assertThat(writer.firstTrace().size()).isEqualTo(3);
		long parentId = writer.firstTrace().get(0).context().getTraceId();
		
		assertThat(writer.firstTrace().get(0).getOperationName()).isEqualTo("NEW_TRACE");
		assertThat(writer.firstTrace().get(0).getParentId()).isEqualTo(0);//ROOT / no parent
		assertThat(writer.firstTrace().get(0).context().getParentId()).isEqualTo(0);
		assertThat(writer.firstTrace().get(0).getServiceName()).isEqualTo("test2");
		
		assertThat(writer.firstTrace().get(1).getOperationName()).isEqualTo("SAY_HELLO");
		assertThat(writer.firstTrace().get(1).getServiceName()).isEqualTo("test");
		assertThat(writer.firstTrace().get(1).getParentId()).isEqualTo(parentId);
		
		assertThat(writer.firstTrace().get(2).getOperationName()).isEqualTo("SAY_HA");
		assertThat(writer.firstTrace().get(2).getParentId()).isEqualTo(parentId);
		assertThat(writer.firstTrace().get(2).context().getSpanType()).isEqualTo("DB");
		
		writer.start();
	}

}