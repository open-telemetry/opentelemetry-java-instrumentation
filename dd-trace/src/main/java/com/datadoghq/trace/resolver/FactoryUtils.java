package com.datadoghq.trace.resolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FactoryUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  public static <A> A loadConfigFromFilePropertyOrResource(
      final String systemProperty, final String resourceName, final Class<A> targetClass) {
    return loadConfigFromFilePropertyOrResource(
        systemProperty, resourceName, new TypeReference<A>() {});
  }

  public static <A> A loadConfigFromFilePropertyOrResource(
      final String systemProperty, final String resourceName, final TypeReference type) {
    final String filePath = System.getProperty(systemProperty);
    if (filePath != null) {
      try {
        log.info("Loading config from file " + filePath);
        return objectMapper.readValue(new File(filePath), type);
      } catch (final Exception e) {
        log.error(
            "Cannot read provided configuration file " + filePath + ". Using the default one.", e);
      }
    }

    return loadConfigFromResource(resourceName, type);
  }

  public static <A> A loadConfigFromResource(
      final String resourceName, final Class<A> targetClass) {
    return loadConfigFromResource(resourceName, new TypeReference<A>() {});
  }

  public static <A> A loadConfigFromResource(final String resourceName, final TypeReference type) {
    A config = null;

    // Try loading both suffixes
    if (!resourceName.endsWith(".yaml") && !resourceName.endsWith(".yml")) {
      config = loadConfigFromResource(resourceName + ".yaml", type);
      if (config == null) {
        config = loadConfigFromResource(resourceName + ".yml", type);
      }
      if (config != null) {
        return config;
      }
    }

    try {
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      final URL resource = classLoader.getResource(resourceName);
      if (resource != null) {
        log.info("Loading config from resource " + resource);
        config = objectMapper.readValue(resource.openStream(), type);
      }
    } catch (final IOException e) {
      log.warn("Could not load configuration file {}.", resourceName);
      log.error("Error when loading config file", e);
    }
    return config;
  }
}
