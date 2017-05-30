package com.datadoghq.trace.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.opentracing.tag.Tags;
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

		// for HTTP decorators
		List<DDSpanContextDecorator> decorators = tracer.getSpanContextDecorators(Tags.COMPONENT.getKey());

		assertThat(decorators.size()).isEqualTo(2);
		DDSpanContextDecorator decorator = decorators.get(0);
		assertThat(decorator.getClass()).isEqualTo(HTTPComponent.class);
		HTTPComponent httpServiceDecorator = (HTTPComponent) decorator;
		assertThat(httpServiceDecorator.getMatchingTag()).isEqualTo("component");
		assertThat(httpServiceDecorator.getMatchingValue()).isEqualTo("hello");
		assertThat(httpServiceDecorator.getSetValue()).isEqualTo("world");

		// for URL decorators
		decorators = tracer.getSpanContextDecorators(Tags.HTTP_URL.getKey());
		assertThat(decorators.size()).isEqualTo(1);

		decorator = decorators.get(0);
		assertThat(decorator.getClass()).isEqualTo(URLAsResourceName.class);
		
	}

}
