package datadog.trace.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  public static <A> A loadConfigFromResource(
      final String resourceName, final Class<A> targetClass) {
    A config = null;

    // Try loading both suffixes
    if (!resourceName.endsWith(".yaml") && !resourceName.endsWith(".yml")) {
      config = loadConfigFromResource(resourceName + ".yaml", targetClass);
      if (config == null) {
        config = loadConfigFromResource(resourceName + ".yml", targetClass);
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
        config = objectMapper.readValue(resource.openStream(), targetClass);
      }
    } catch (final IOException e) {
      log.warn("Could not load configuration file {}.", resourceName);
      log.error("Error when loading config file", e);
    }
    return config;
  }
}
