package com.datadoghq.trace.resolver;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Create DDSpaDecorators from a valid configuration
 */
public class DDDecoratorsFactory {

	private final static Logger logger = LoggerFactory.getLogger(DDDecoratorsFactory.class);

	public static String DECORATORS_PACKAGE = "com.datadoghq.trace.integration.";

	public static final String CONFIG_PATH = "dd-trace-decorators.yaml";

	private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

	/**
	 * Create decorators from configuration
	 * 
	 * @param decoratorsConfig
	 * @return the list of instanciated & configured decorators
	 */
	public static List<DDSpanContextDecorator> create(List<DDSpanDecoratorConfig> decoratorsConfig){
		List<DDSpanContextDecorator> decorators = new ArrayList<DDSpanContextDecorator>();
		for (DDSpanDecoratorConfig decoratorConfig : decoratorsConfig) {
			if(decoratorConfig.getType()==null){
				logger.warn("Cannot create decorator without type from configuration {}",decoratorConfig);
				continue;
			}

			//Find class and create
			Class<?> decoratorClass;
			try {
				decoratorClass = Class.forName(DECORATORS_PACKAGE+decoratorConfig.getType());
			} catch (ClassNotFoundException e) {
				logger.warn("Cannot create decorator as the class {} is not defined. Provided configuration {}",decoratorConfig);
				continue;
			}

			DDSpanContextDecorator decorator = null;
			try{
				decorator = (DDSpanContextDecorator) decoratorClass.getConstructor().newInstance();
			}catch(Exception e){
				logger.warn("Cannot create decorator as we could not invoke the default constructor. Provided configuration {}",decoratorConfig);
				continue;
			}

			//Fill with config values
			if(decoratorConfig.getMatchingTag()!=null){
				decorator.setMatchingTag(decoratorConfig.getMatchingTag());
			}
			if(decoratorConfig.getMatchingValue()!=null){
				decorator.setMatchingValue(decoratorConfig.getMatchingValue());
			}
			if(decoratorConfig.getSetTag()!=null){
				decorator.setSetTag(decoratorConfig.getSetTag());
			}
			if(decoratorConfig.getSetValue()!=null){
				decorator.setSetValue(decoratorConfig.getSetValue());
			}

			decorators.add(decorator);
		}
		return decorators;
	}

	public static List<DDSpanContextDecorator> createFromResources(){
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		List<DDSpanContextDecorator> result = new ArrayList<DDSpanContextDecorator>();
		try{
			Enumeration<URL> iter = classLoader.getResources(CONFIG_PATH);
			while (iter.hasMoreElements()) {
				TracerConfig config = objectMapper.readValue(iter.nextElement().openStream(), TracerConfig.class);
				result = DDDecoratorsFactory.create(config.getDecorators());
				
				break; // ONLY the closest resource file is taken into account
			}
		}catch(IOException e){
			logger.error("Could not load decorators configuration file.", e);
		}


		return result;
	}
}
