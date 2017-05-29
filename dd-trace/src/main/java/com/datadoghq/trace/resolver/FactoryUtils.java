package com.datadoghq.trace.resolver;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class FactoryUtils {
	private final static Logger logger = LoggerFactory.getLogger(FactoryUtils.class);

	private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
	
	public static <A> A loadConfigFromResource(String resourceName, Class<A> targetClass){
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		A config = null;
		try {
			Enumeration<URL> iter = classLoader.getResources(resourceName);
			while (iter.hasMoreElements()) {
				config = objectMapper.readValue(iter.nextElement().openStream(), targetClass);

				break; // ONLY the closest resource file is taken into account
			}
		} catch (IOException e) {
			logger.warn("Could not load configuration file {}.", resourceName);
			logger.error("Error when loading config file", e);
		}
		return config;
	}
}
