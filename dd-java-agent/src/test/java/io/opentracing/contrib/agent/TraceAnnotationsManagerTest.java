package io.opentracing.contrib.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

public class TraceAnnotationsManagerTest {

	protected MockTracer tracer = new MockTracer();

	@Before
	public void beforeTest() throws Exception {
		GlobalTracer.register(tracer);
		TraceAnnotationsManager.loadRules(ClassLoader.getSystemClassLoader());
	}

	@Test
	public void test() {
		//Test single span in new trace
		SayTracedHello.sayHello();
		
		assertThat(tracer.finishedSpans().size()).isEqualTo(1);
		assertThat(tracer.finishedSpans().get(0).operationName()).isEqualTo("SAY_HELLO");
		assertThat(tracer.finishedSpans().get(0).tags().get("service-name")).isEqualTo("test");
		
		tracer.reset();

		//Test new trace with 2 children spans
		SayTracedHello.sayHELLOsayHA();
		assertThat(tracer.finishedSpans().size()).isEqualTo(3);
		assertThat(tracer.finishedSpans().get(0).operationName()).isEqualTo("SAY_HELLO");
		assertThat(tracer.finishedSpans().get(0).tags().get("service-name")).isEqualTo("test");
		
		long traceId = tracer.finishedSpans().get(0).context().traceId();
		long parentId = tracer.finishedSpans().get(0).parentId();
		
		assertThat(tracer.finishedSpans().get(1).operationName()).isEqualTo("SAY_HA");
		assertThat(tracer.finishedSpans().get(1).parentId()).isEqualTo(parentId);
		assertThat(tracer.finishedSpans().get(1).context().traceId()).isEqualTo(traceId);
		assertThat(tracer.finishedSpans().get(1).tags().get("span-type")).isEqualTo("DB");
		assertThat(tracer.finishedSpans().get(1).tags().get("service-name")).isEqualTo("test");
		
		assertThat(tracer.finishedSpans().get(2).operationName()).isEqualTo("NEW_TRACE");
		assertThat(tracer.finishedSpans().get(2).parentId()).isEqualTo(0);//ROOT / no parent
		assertThat(tracer.finishedSpans().get(2).context().traceId()).isEqualTo(traceId);
		assertThat(tracer.finishedSpans().get(2).tags().get("service-name")).isEqualTo("test2");
		
		
		System.out.println(tracer.finishedSpans());
		tracer.reset();
	}

}