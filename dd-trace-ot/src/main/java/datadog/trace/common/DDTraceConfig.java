package datadog.trace.common;

import static datadog.trace.common.util.Config.getPropOrEnv;

import datadog.opentracing.DDTracer;
import datadog.trace.common.writer.DDAgentWriter;
import datadog.trace.common.writer.Writer;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * Config gives priority to system properties and falls back to environment variables. It also
 * includes default values to ensure a valid config.
 *
 * <p>
 *
 * <p>System properties are {@link DDTraceConfig#PREFIX}'ed. Environment variables are the same as
 * the system property, but uppercased with '.' -> '_'.
 */
@Slf4j
public class DDTraceConfig extends Properties {
  /** Config keys below */
  private static final String PREFIX = "dd.";

  public static final String SERVICE_NAME = "service.name";
  public static final String SERVICE_MAPPING = "service.mapping";
  public static final String WRITER_TYPE = "writer.type";
  public static final String AGENT_HOST = "agent.host";
  public static final String AGENT_PORT = "agent.port";
  public static final String PRIORITY_SAMPLING = "priority.sampling";
  public static final String SPAN_TAGS = "trace.span.tags";
  public static final String HEADER_TAGS = "trace.header.tags";

  private final String serviceName = getPropOrEnv(PREFIX + SERVICE_NAME);
  private final String serviceMapping = getPropOrEnv(PREFIX + SERVICE_MAPPING);
  private final String writerType = getPropOrEnv(PREFIX + WRITER_TYPE);
  private final String agentHost = getPropOrEnv(PREFIX + AGENT_HOST);
  private final String agentPort = getPropOrEnv(PREFIX + AGENT_PORT);
  private final String prioritySampling = getPropOrEnv(PREFIX + PRIORITY_SAMPLING);
  private final String spanTags = getPropOrEnv(PREFIX + SPAN_TAGS);
  private final String headerTags = getPropOrEnv(PREFIX + HEADER_TAGS);

  public DDTraceConfig() {
    super();

    final Properties defaults = new Properties();
    defaults.setProperty(SERVICE_NAME, DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME);
    defaults.setProperty(WRITER_TYPE, Writer.DD_AGENT_WRITER_TYPE);
    defaults.setProperty(AGENT_HOST, DDAgentWriter.DEFAULT_HOSTNAME);
    defaults.setProperty(AGENT_PORT, String.valueOf(DDAgentWriter.DEFAULT_PORT));
    super.defaults = defaults;

    setIfNotNull(SERVICE_NAME, serviceName);
    setIfNotNull(SERVICE_MAPPING, serviceMapping);
    setIfNotNull(WRITER_TYPE, writerType);
    setIfNotNull(AGENT_HOST, agentHost);
    setIfNotNull(AGENT_PORT, agentPort);
    setIfNotNull(PRIORITY_SAMPLING, prioritySampling);
    setIfNotNull(SPAN_TAGS, spanTags);
    setIfNotNull(HEADER_TAGS, headerTags);
  }

  public DDTraceConfig(final String serviceName) {
    this();
    put(SERVICE_NAME, serviceName);
  }

  private void setIfNotNull(final String key, final String value) {
    if (value != null) {
      setProperty(key, value);
    }
  }
}
