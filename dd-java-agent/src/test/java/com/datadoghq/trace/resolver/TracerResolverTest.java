package com.datadoghq.trace.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.datadoghq.trace.integration.HTTPComponent;
import com.datadoghq.trace.integration.URLAsResourceName;

public class TracerResolverTest {

	@Test
	public void test() {
		DDTracerResolver tracerResolver = new DDTracerResolver();
		DDTracer tracer = (DDTracer) tracerResolver.resolve();
		
		List<DDSpanContextDecorator> decorators = tracer.getSpanContextDecorators();
		
		assertThat(decorators.size()).isEqualTo(2);
		DDSpanContextDecorator decorator = decorators.get(0);
		assertThat(decorator.getClass()).isEqualTo(HTTPComponent.class);
		HTTPComponent httpServiceDecorator = (HTTPComponent) decorator;
		
		assertThat(httpServiceDecorator.getMatchingTag()).isEqualTo("component");
		assertThat(httpServiceDecorator.getMatchingValue()).isEqualTo("hello");
		assertThat(httpServiceDecorator.getSetValue()).isEqualTo("world");
		
		decorator = decorators.get(1);
		assertThat(decorator.getClass()).isEqualTo(URLAsResourceName.class);
		
	}

}
