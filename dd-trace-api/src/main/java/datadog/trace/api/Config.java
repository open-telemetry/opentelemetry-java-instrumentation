package datadog.trace.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Config gives priority to system properties and falls back to environment variables. It also
 * includes default values to ensure a valid config.
 *
 * <p>
 *
 * <p>System properties are {@link Config#PREFIX}'ed. Environment variables are the same as the
 * system property, but uppercased with '.' -> '_'.
 */
@Slf4j
@ToString(includeFieldNames = true)
public class Config {
  /** Config keys below */
  private static final String PREFIX = "dd.";

  private static final Config INSTANCE = new Config();

  public static final String SERVICE_NAME = "service.name";
  public static final String WRITER_TYPE = "writer.type";
  public static final String AGENT_HOST = "agent.host";
  public static final String TRACE_AGENT_PORT = "trace.agent.port";
  public static final String AGENT_PORT_LEGACY = "agent.port";
  public static final String PRIORITY_SAMPLING = "priority.sampling";
  public static final String TRACE_RESOLVER_ENABLED = "trace.resolver.enabled";
  public static final String SERVICE_MAPPING = "service.mapping";
  public static final String GLOBAL_TAGS = "trace.global.tags";
  public static final String SPAN_TAGS = "trace.span.tags";
  public static final String JMX_TAGS = "trace.jmx.tags";
  public static final String HEADER_TAGS = "trace.header.tags";
  public static final String RUNTIME_CONTEXT_FIELD_INJECTION =
      "trace.runtime.context.field.injection";
  public static final String JMX_FETCH_ENABLED = "jmxfetch.enabled";
  public static final String JMX_FETCH_METRICS_CONFIGS = "jmxfetch.metrics-configs";
  public static final String JMX_FETCH_CHECK_PERIOD = "jmxfetch.check-period";
  public static final String JMX_FETCH_REFRESH_BEANS_PERIOD = "jmxfetch.refresh-beans-period";
  public static final String JMX_FETCH_STATSD_HOST = "jmxfetch.statsd.host";
  public static final String JMX_FETCH_STATSD_PORT = "jmxfetch.statsd.port";

  public static final String RUNTIME_ID_TAG = "runtime-id";
  public static final String DEFAULT_SERVICE_NAME = "unnamed-java-app";

  public static final String DD_AGENT_WRITER_TYPE = "DDAgentWriter";
  public static final String LOGGING_WRITER_TYPE = "LoggingWriter";
  public static final String DEFAULT_AGENT_WRITER_TYPE = DD_AGENT_WRITER_TYPE;

  public static final String DEFAULT_AGENT_HOST = "localhost";
  public static final int DEFAULT_TRACE_AGENT_PORT = 8126;

  private static final boolean DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION = true;

  private static final boolean DEFAULT_PRIORITY_SAMPLING_ENABLED = false;
  private static final boolean DEFAULT_TRACE_RESOLVER_ENABLED = true;
  private static final boolean DEFAULT_JMX_FETCH_ENABLED = false;

  public static final int DEFAULT_JMX_FETCH_STATSD_PORT = 8125;

  /**
   * this is a random UUID that gets generated on JVM start up and is attached to every root span
   * and every JMX metric that is sent out.
   */
  @Getter private final String runtimeId;

  @Getter private final String serviceName;
  @Getter private final String writerType;
  @Getter private final String agentHost;
  @Getter private final int agentPort;
  @Getter private final boolean prioritySamplingEnabled;
  @Getter private final boolean traceResolverEnabled;
  @Getter private final Map<String, String> serviceMapping;
  private final Map<String, String> globalTags;
  private final Map<String, String> spanTags;
  private final Map<String, String> jmxTags;
  @Getter private final Map<String, String> headerTags;
  @Getter private final boolean runtimeContextFieldInjection;
  @Getter private final boolean jmxFetchEnabled;
  @Getter private final List<String> jmxFetchMetricsConfigs;
  @Getter private final Integer jmxFetchCheckPeriod;
  @Getter private final Integer jmxFetchRefreshBeansPeriod;
  @Getter private final String jmxFetchStatsdHost;
  @Getter private final Integer jmxFetchStatsdPort;

  // Read order: System Properties -> Env Variables, [-> default value]
  // Visible for testing
  Config() {
    runtimeId = UUID.randomUUID().toString();

    serviceName = getSettingFromEnvironment(SERVICE_NAME, DEFAULT_SERVICE_NAME);
    writerType = getSettingFromEnvironment(WRITER_TYPE, DEFAULT_AGENT_WRITER_TYPE);
    agentHost = getSettingFromEnvironment(AGENT_HOST, DEFAULT_AGENT_HOST);
    agentPort =
        getIntegerSettingFromEnvironment(
            TRACE_AGENT_PORT,
            getIntegerSettingFromEnvironment(AGENT_PORT_LEGACY, DEFAULT_TRACE_AGENT_PORT));
    prioritySamplingEnabled =
        getBooleanSettingFromEnvironment(PRIORITY_SAMPLING, DEFAULT_PRIORITY_SAMPLING_ENABLED);
    traceResolverEnabled =
        getBooleanSettingFromEnvironment(TRACE_RESOLVER_ENABLED, DEFAULT_TRACE_RESOLVER_ENABLED);
    serviceMapping = getMapSettingFromEnvironment(SERVICE_MAPPING, null);

    globalTags = getMapSettingFromEnvironment(GLOBAL_TAGS, null);
    spanTags = getMapSettingFromEnvironment(SPAN_TAGS, null);
    jmxTags = getMapSettingFromEnvironment(JMX_TAGS, null);
    headerTags = getMapSettingFromEnvironment(HEADER_TAGS, null);

    runtimeContextFieldInjection =
        getBooleanSettingFromEnvironment(
            RUNTIME_CONTEXT_FIELD_INJECTION, DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION);

    jmxFetchEnabled =
        getBooleanSettingFromEnvironment(JMX_FETCH_ENABLED, DEFAULT_JMX_FETCH_ENABLED);
    jmxFetchMetricsConfigs = getListSettingFromEnvironment(JMX_FETCH_METRICS_CONFIGS, null);
    jmxFetchCheckPeriod = getIntegerSettingFromEnvironment(JMX_FETCH_CHECK_PERIOD, null);
    jmxFetchRefreshBeansPeriod =
        getIntegerSettingFromEnvironment(JMX_FETCH_REFRESH_BEANS_PERIOD, null);
    jmxFetchStatsdHost = getSettingFromEnvironment(JMX_FETCH_STATSD_HOST, null);
    jmxFetchStatsdPort =
        getIntegerSettingFromEnvironment(JMX_FETCH_STATSD_PORT, DEFAULT_JMX_FETCH_STATSD_PORT);
  }

  // Read order: Properties -> Parent
  private Config(final Properties properties, final Config parent) {
    runtimeId = parent.runtimeId;

    serviceName = properties.getProperty(SERVICE_NAME, parent.serviceName);
    writerType = properties.getProperty(WRITER_TYPE, parent.writerType);
    agentHost = properties.getProperty(AGENT_HOST, parent.agentHost);
    agentPort =
        getPropertyIntegerValue(
            properties,
            TRACE_AGENT_PORT,
            getPropertyIntegerValue(properties, AGENT_PORT_LEGACY, parent.agentPort));
    prioritySamplingEnabled =
        getPropertyBooleanValue(properties, PRIORITY_SAMPLING, parent.prioritySamplingEnabled);
    traceResolverEnabled =
        getPropertyBooleanValue(properties, TRACE_RESOLVER_ENABLED, parent.traceResolverEnabled);
    serviceMapping = getPropertyMapValue(properties, SERVICE_MAPPING, parent.serviceMapping);

    globalTags = getPropertyMapValue(properties, GLOBAL_TAGS, parent.globalTags);
    spanTags = getPropertyMapValue(properties, SPAN_TAGS, parent.spanTags);
    jmxTags = getPropertyMapValue(properties, JMX_TAGS, parent.jmxTags);
    headerTags = getPropertyMapValue(properties, HEADER_TAGS, parent.headerTags);

    runtimeContextFieldInjection =
        getPropertyBooleanValue(
            properties, RUNTIME_CONTEXT_FIELD_INJECTION, parent.runtimeContextFieldInjection);

    jmxFetchEnabled =
        getPropertyBooleanValue(properties, JMX_FETCH_ENABLED, parent.jmxFetchEnabled);
    jmxFetchMetricsConfigs =
        getPropertyListValue(properties, JMX_FETCH_METRICS_CONFIGS, parent.jmxFetchMetricsConfigs);
    jmxFetchCheckPeriod =
        getPropertyIntegerValue(properties, JMX_FETCH_CHECK_PERIOD, parent.jmxFetchCheckPeriod);
    jmxFetchRefreshBeansPeriod =
        getPropertyIntegerValue(
            properties, JMX_FETCH_REFRESH_BEANS_PERIOD, parent.jmxFetchRefreshBeansPeriod);
    jmxFetchStatsdHost = properties.getProperty(JMX_FETCH_STATSD_HOST, parent.jmxFetchStatsdHost);
    jmxFetchStatsdPort =
        getPropertyIntegerValue(properties, JMX_FETCH_STATSD_PORT, parent.jmxFetchStatsdPort);
  }

  public Map<String, String> getMergedSpanTags() {
    // DO not include runtimeId into span tags: we only want that added to the root span
    final Map<String, String> result = newHashMap(globalTags.size() + spanTags.size());
    result.putAll(globalTags);
    result.putAll(spanTags);
    return Collections.unmodifiableMap(result);
  }

  public Map<String, String> getMergedJmxTags() {
    final Map<String, String> result = newHashMap(globalTags.size() + jmxTags.size() + 1);
    result.putAll(globalTags);
    result.putAll(jmxTags);
    result.put(RUNTIME_ID_TAG, runtimeId);
    result.put(SERVICE_NAME, serviceName);
    return Collections.unmodifiableMap(result);
  }

  private static String getSettingFromEnvironment(final String name, final String defaultValue) {
    final String completeName = PREFIX + name;
    final String value =
        System.getProperties()
            .getProperty(completeName, System.getenv(propertyToEnvironmentName(completeName)));
    return value == null ? defaultValue : value;
  }

  private static Map<String, String> getMapSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseMap(getSettingFromEnvironment(name, defaultValue), PREFIX + name);
  }

  private static List<String> getListSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseList(getSettingFromEnvironment(name, defaultValue));
  }

  private static Boolean getBooleanSettingFromEnvironment(
      final String name, final Boolean defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  private static Integer getIntegerSettingFromEnvironment(
      final String name, final Integer defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  private static String propertyToEnvironmentName(final String name) {
    return name.toUpperCase().replace(".", "_").replace("-", "_");
  }

  private static Map<String, String> getPropertyMapValue(
      final Properties properties, final String name, final Map<String, String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null ? defaultValue : parseMap(value, name);
  }

  private static List<String> getPropertyListValue(
      final Properties properties, final String name, final List<String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null ? defaultValue : parseList(value);
  }

  private static Boolean getPropertyBooleanValue(
      final Properties properties, final String name, final Boolean defaultValue) {
    final String value = properties.getProperty(name);
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  private static Integer getPropertyIntegerValue(
      final Properties properties, final String name, final Integer defaultValue) {
    final String value = properties.getProperty(name);
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  private static Map<String, String> parseMap(final String str, final String settingName) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyMap();
    }
    if (!str.matches("(([^,:]+:[^,:]*,)*([^,:]+:[^,:]*),?)?")) {
      log.warn(
          "Invalid config for {}: '{}'. Must match 'key1:value1,key2:value2'.", settingName, str);
      return Collections.emptyMap();
    }

    final String[] tokens = str.split(",", -1);
    final Map<String, String> map = newHashMap(tokens.length);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", -1);
      if (keyValue.length == 2) {
        final String key = keyValue[0].trim();
        final String value = keyValue[1].trim();
        if (value.length() <= 0) {
          log.warn("Ignoring empty value for key '{}' in config for {}", key, settingName);
          continue;
        }
        map.put(key, value);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  private static Map<String, String> newHashMap(final int size) {
    return new HashMap<>(size + 1, 1f);
  }

  private static List<String> parseList(final String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyList();
    }

    final String[] tokens = str.split(",", -1);
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  public static Config get() {
    return INSTANCE;
  }

  public static Config get(final Properties properties) {
    if (properties == null || properties.isEmpty()) {
      return INSTANCE;
    } else {
      return new Config(properties, INSTANCE);
    }
  }
}
