package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import com.datadoghq.trace.writer.LoggingWritter;
import com.datadoghq.trace.writer.Writer;

/**
 * Create a tracer from a configuration file
 */
public class DDTracerFactory {
	
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
	
}
