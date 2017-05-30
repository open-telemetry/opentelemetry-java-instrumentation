package com.datadoghq.trace.resolver;

import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.google.auto.service.AutoService;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;


@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {

	private final static Logger logger = LoggerFactory.getLogger(DDTracerResolver.class);

	@Override
	protected Tracer resolve() {
		logger.info("Creating the Datadog tracer");

		//Find a resource file named dd-trace.yml
		DDTracer tracer = null;
		//Create tracer from resource files
		tracer = DDTracerFactory.createFromResources();

		//Create decorators from resource files
		List<DDSpanContextDecorator> decorators = DDDecoratorsFactory.createFromResources();
		for(DDSpanContextDecorator decorator : decorators){
			tracer.addDecorator(decorator);
		}

		return tracer;
	}

	@SuppressWarnings("static-access")
	public static Tracer registerTracer() {

		ServiceLoader<TracerResolver> RESOLVERS = ServiceLoader.load(TracerResolver.class);

		Tracer tracer = null;
		for (TracerResolver value : RESOLVERS) {
			tracer = value.resolveTracer();
			if (tracer != null) {
				break;
			}
		}

		if (tracer == null) {
			tracer = NoopTracerFactory.create();
		}

		GlobalTracer.register(tracer);
		return tracer;
	}
}
