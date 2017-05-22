package com.datadoghq.trace.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.datadoghq.trace.integration.HTTPServiceDecorator;

public class TracerResolverTest {

	@Test
	public void test() {
		DDTracerResolver tracerResolver = new DDTracerResolver();
		DDTracer tracer = (DDTracer) tracerResolver.resolve();
		
		List<DDSpanContextDecorator> decorators = tracer.getSpanContextDecorators();
		
		assertThat(decorators.size()).isEqualTo(1);
		DDSpanContextDecorator decorator = decorators.get(0);
		assertThat(decorator.getClass()).isEqualTo(HTTPServiceDecorator.class);
		HTTPServiceDecorator httpServiceDecorator = (HTTPServiceDecorator) decorator;
		assertThat(httpServiceDecorator.getComponentName()).isEqualTo("hello");
		assertThat(httpServiceDecorator.getDesiredServiceName()).isEqualTo("world");
	}

}
