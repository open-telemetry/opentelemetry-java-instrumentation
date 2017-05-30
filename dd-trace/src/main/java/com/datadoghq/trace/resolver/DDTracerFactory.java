package com.datadoghq.trace.resolver;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.ASampler;
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

	private final static Logger logger = LoggerFactory.getLogger(DDTracerFactory.class);

	public static final String CONFIG_PATH = "dd-trace.yaml";

	/**
	 * Create a tracer from a TracerConfig object
	 * 
	 * @param config
	 * @return the corresponding tracer
	 */
	@SuppressWarnings("unchecked")
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
		Sampler sampler = DDTracer.UNASSIGNED_SAMPLER;
		if (config.getSampler() != null && config.getSampler().get("type") != null) {
			String type = (String) config.getSampler().get("type");
			if (type.equals(AllSampler.class.getSimpleName())) {
				sampler = new AllSampler();
			} else if (type.equals(RateSampler.class.getSimpleName())) {
				sampler = new RateSampler((Double) config.getSampler().get("rate"));
			}
			
			//Add sampled tags
			Map<String,String> skipTagsPatterns = (Map<String, String>) config.getSampler().get("skipTagsPatterns");
			if(skipTagsPatterns!=null && sampler instanceof ASampler){
				ASampler aSampler = (ASampler) sampler;
				for(Entry<String,String> entry:skipTagsPatterns.entrySet()){
					aSampler.addSkipTagPattern(entry.getKey(), Pattern.compile(entry.getValue()));
				}
			}
		}

		//Create tracer
		return new DDTracer(defaultServiceName, writer, sampler);
	}


	
	public static DDTracer createFromResources(){
		TracerConfig tracerConfig = FactoryUtils.loadConfigFromResource(CONFIG_PATH, TracerConfig.class);
		
		DDTracer tracer = null;
		if (tracerConfig == null) {
			logger.info("No valid configuration file {} found. Loading default tracer.",CONFIG_PATH);
			tracer = new DDTracer();
		}else{
			tracer = DDTracerFactory.create(tracerConfig);
		}
		
		return tracer;
	}

}
