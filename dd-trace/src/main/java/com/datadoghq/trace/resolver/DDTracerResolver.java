package com.datadoghq.trace.resolver;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.auto.service.AutoService;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;


@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {

	private final static Logger logger = LoggerFactory.getLogger(DDTracerResolver.class);

	public static final String TRACER_CONFIG = "dd-trace.yaml";
	public static final String DECORATORS_CONFIG = "dd-trace-decorators.yaml";

	private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

	@Override
	protected Tracer resolve() {
		logger.info("Creating the Datadog tracer");

		//Find a resource file named dd-trace.yml
		DDTracer tracer = null;
		try {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			Enumeration<URL> iter = classLoader.getResources(TRACER_CONFIG);
			while (iter.hasMoreElements()) {
				TracerConfig config = objectMapper.readValue(iter.nextElement().openStream(), TracerConfig.class);

				tracer = DDTracerFactory.create(config);

				break; // ONLY the closest resource file is taken into account
			}

			iter = classLoader.getResources(DECORATORS_CONFIG);
			while (iter.hasMoreElements()) {
				TracerConfig config = objectMapper.readValue(iter.nextElement().openStream(), TracerConfig.class);
				//Find decorators
				if (config.getDecorators() != null) {
					for(DDSpanContextDecorator decorator:DDDecoratorsFactory.create(config.getDecorators())){
						tracer.addDecorator(decorator);
					}
				}
				
				break; // ONLY the closest resource file is taken into account
			}
		} catch (IOException e) {
			logger.error("Could not load tracer configuration file. Loading default tracer.", e);
		}

		if (tracer == null) {
			logger.info("No valid configuration file 'dd-trace.yaml' found. Loading default tracer.");
			tracer = new DDTracer();
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
