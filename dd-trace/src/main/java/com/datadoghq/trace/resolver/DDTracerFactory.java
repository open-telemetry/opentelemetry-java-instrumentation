package com.datadoghq.trace.resolver;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import com.datadoghq.trace.writer.LoggingWritter;
import com.datadoghq.trace.writer.Writer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Create a tracer from a configuration file
 */
public class DDTracerFactory {

	private final static Logger logger = LoggerFactory.getLogger(DDTracerFactory.class);

	public static final String CONFIG_PATH = "dd-trace.yaml";
	private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());


	/**
	 * Create a tracer from a TracerConfig object
	 * 
	 * @param config
	 * @return the corresponding tracer
	 */
	public static DDTracer create(TracerConfig config){
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
		return new DDTracer(defaultServiceName, writer, rateSampler);
	}
	
	public static DDTracer createFromResources(){
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		DDTracer tracer = null;
		try {
			Enumeration<URL> iter = classLoader.getResources(CONFIG_PATH);
			while (iter.hasMoreElements()) {
				TracerConfig config = objectMapper.readValue(iter.nextElement().openStream(), TracerConfig.class);

				tracer = DDTracerFactory.create(config);

				break; // ONLY the closest resource file is taken into account
			}
		} catch (IOException e) {
			logger.error("Could not load tracer configuration file.", e);
		}

		if (tracer == null) {
			logger.info("No valid configuration file {} found. Loading default tracer.",CONFIG_PATH);
			tracer = new DDTracer();
		}
		return tracer;
	}

}
