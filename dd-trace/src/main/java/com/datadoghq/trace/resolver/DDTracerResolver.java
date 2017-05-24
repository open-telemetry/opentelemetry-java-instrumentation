package com.datadoghq.trace.resolver;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DB;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.datadoghq.trace.integration.HTTP;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import com.datadoghq.trace.writer.LoggingWritter;
import com.datadoghq.trace.writer.Writer;
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

				String defaultServiceName = config.getDefaultServiceName() != null ? config.getDefaultServiceName() : DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME;

				//Create writer
				Writer writer = DDTracer.UNASSIGNED_WRITER;
				if (config.getWriter() != null && config.getWriter().get("type") != null) {
					String type = (String) config.getWriter().get("type");
					if (type.equals(DDAgentWriter.class.getSimpleName())) {
						String host = config.getWriter().get("host") != null ? (String) config.getWriter().get("host") : DDAgentWriter.DEFAULT_HOSTNAME;
						Integer port = config.getWriter().get("port") != null ? (Integer) config.getWriter().get("port") : DDAgentWriter.DEFAULT_PORT;
						DDApi api = new DDApi(host, port);
						writer = new DDAgentWriter(api);
					} else if (type.equals(LoggingWritter.class.getSimpleName())) {
						writer = new LoggingWritter();
					}
				}

				//Create sampler
				Sampler rateSampler = DDTracer.UNASSIGNED_SAMPLER;
				if (config.getSampler() != null && config.getSampler().get("type") != null) {
					String type = (String) config.getSampler().get("type");
					if (type.equals(AllSampler.class.getSimpleName())) {
						rateSampler = new AllSampler();
					} else if (type.equals(RateSampler.class.getSimpleName())) {
						rateSampler = new RateSampler((Double) config.getSampler().get("rate"));
					}
				}

				//Create tracer
				tracer = new DDTracer(defaultServiceName, writer, rateSampler);

				break; // ONLY the closest resource file is taken into account
			}

			iter = classLoader.getResources(DECORATORS_CONFIG);
			while (iter.hasMoreElements()) {
				TracerConfig config = objectMapper.readValue(iter.nextElement().openStream(), TracerConfig.class);
				//Find decorators
				if (config.getDecorators() != null) {
					for (Map<String, Object> map : config.getDecorators()) {
						if (map.get("type") != null) {
							DDSpanContextDecorator decorator = null;
							String componentName = (String) map.get("componentName");
							String desiredServiceName = (String) map.get("desiredServiceName");

							if (map.get("type").equals(HTTP.class.getSimpleName())) {
								decorator = new HTTP(componentName, desiredServiceName);
								tracer.addDecorator(decorator);
							} else if (map.get("type").equals(DB.class.getSimpleName())) {
								decorator = new DB(componentName, desiredServiceName);
								tracer.addDecorator(decorator);
							}
						}
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
