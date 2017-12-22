package com.datadoghq.trace;

import java.util.Properties;

/**
 * Config gives priority to system properties and falls back to environment variables.
 *
 * <p>System properties are {@link DDTraceConfig#PREFIX}'ed. Environment variables are the same as
 * the system property, but uppercased with '.' -> '_'.
 */
public class DDTraceConfig extends Properties {
  /** Config keys bel */
  private static final String PREFIX = "dd.";

  public static final String SERVICE_NAME = "service.name";
  public static final String WRITER_TYPE = "writer.type";
  public static final String AGENT_HOST = "agent.host";
  public static final String AGENT_PORT = "agent.port";
  public static final String SAMPLER_TYPE = "sampler.type";
  public static final String SAMPLER_RATE = "sampler.rate";

  private final String serviceName = getPropOrEnv(PREFIX + SERVICE_NAME);
  private final String writerType = getPropOrEnv(PREFIX + WRITER_TYPE);
  private final String agentHost = getPropOrEnv(PREFIX + AGENT_HOST);
  private final String agentPort = getPropOrEnv(PREFIX + AGENT_PORT);
  private final String samplerType = getPropOrEnv(PREFIX + SAMPLER_TYPE);
  private final String samplerRate = getPropOrEnv(PREFIX + SAMPLER_RATE);

  public DDTraceConfig() {
    super();

    final Properties defaults = new Properties();
    defaults.setProperty(SERVICE_NAME, DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME);
    super.defaults = defaults;

    final Properties baseValues = new Properties(defaults);
    setIfNotNull(SERVICE_NAME, serviceName);
    setIfNotNull(WRITER_TYPE, writerType);
    setIfNotNull(AGENT_HOST, agentHost);
    setIfNotNull(AGENT_PORT, agentPort);
    setIfNotNull(SAMPLER_TYPE, samplerType);
    setIfNotNull(SAMPLER_RATE, samplerRate);
  }

  public DDTraceConfig(final String serviceName) {
    super();
    put(SERVICE_NAME, serviceName);
  }

  private void setIfNotNull(final String key, final String value) {
    if (value != null) {
      setProperty(key, value);
    }
  }

  private String getPropOrEnv(final String name) {
    return System.getProperty(name, System.getenv(propToEnvName(name)));
  }

  static String propToEnvName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }
}
