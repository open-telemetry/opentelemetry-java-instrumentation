package com.datadoghq.trace.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FactoryUtils {
  private static final Logger logger = LoggerFactory.getLogger(FactoryUtils.class);

  private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  public static <A> A loadConfigFromFilePropertyOrResource(
      final String systemProperty, final String resourceName, final Class<A> targetClass) {
    final String filePath = System.getProperty(systemProperty);
    if (filePath != null) {
      try {
        logger.info("Loading config from file " + filePath);
        return objectMapper.readValue(new File(filePath), targetClass);
      } catch (final Exception e) {
        logger.error(
            "Cannot read provided configuration file " + filePath + ". Using the default one.", e);
      }
    }

    return loadConfigFromResource(resourceName, targetClass);
  }

  public static <A> A loadConfigFromResource(
      final String resourceName, final Class<A> targetClass) {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    A config = null;
    try {
      final Enumeration<URL> iter = classLoader.getResources(resourceName);
      if (iter.hasMoreElements()) {
        final URL url = iter.nextElement();
        logger.info("Loading config from resource " + url);
        config = objectMapper.readValue(url.openStream(), targetClass);
      }
    } catch (final IOException e) {
      logger.warn("Could not load configuration file {}.", resourceName);
      logger.error("Error when loading config file", e);
    }
    return config;
  }
}
