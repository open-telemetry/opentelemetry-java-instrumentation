package datadog.trace.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Config reads values with the following priority: 1) system properties, 2) environment variables,
 * 3) optional configuration file. It also includes default values to ensure a valid config.
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

  private static final Pattern ENV_REPLACEMENT = Pattern.compile("[^a-zA-Z0-9_]");

  public static final String CONFIGURATION_FILE = "trace.config";
  public static final String SERVICE_NAME = "service.name";
  public static final String SERVICE = "service";
  public static final String TRACE_ENABLED = "trace.enabled";
  public static final String INTEGRATIONS_ENABLED = "integrations.enabled";
  public static final String WRITER_TYPE = "writer.type";
  public static final String AGENT_HOST = "agent.host";
  public static final String TRACE_AGENT_PORT = "trace.agent.port";
  public static final String AGENT_PORT_LEGACY = "agent.port";
  public static final String AGENT_UNIX_DOMAIN_SOCKET = "trace.agent.unix.domain.socket";
  public static final String PRIORITY_SAMPLING = "priority.sampling";
  public static final String TRACE_RESOLVER_ENABLED = "trace.resolver.enabled";
  public static final String SERVICE_MAPPING = "service.mapping";
  public static final String GLOBAL_TAGS = "trace.global.tags";
  public static final String SPAN_TAGS = "trace.span.tags";
  public static final String JMX_TAGS = "trace.jmx.tags";
  public static final String TRACE_ANALYTICS_ENABLED = "trace.analytics.enabled";
  public static final String TRACE_ANNOTATIONS = "trace.annotations";
  public static final String TRACE_EXECUTORS_ALL = "trace.executors.all";
  public static final String TRACE_EXECUTORS = "trace.executors";
  public static final String TRACE_METHODS = "trace.methods";
  public static final String TRACE_CLASSES_EXCLUDE = "trace.classes.exclude";
  public static final String TRACE_REPORT_HOSTNAME = "trace.report-hostname";
  public static final String HEADER_TAGS = "trace.header.tags";
  public static final String HTTP_SERVER_ERROR_STATUSES = "http.server.error.statuses";
  public static final String HTTP_CLIENT_ERROR_STATUSES = "http.client.error.statuses";
  public static final String HTTP_SERVER_TAG_QUERY_STRING = "http.server.tag.query-string";
  public static final String HTTP_CLIENT_TAG_QUERY_STRING = "http.client.tag.query-string";
  public static final String HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN = "trace.http.client.split-by-domain";
  public static final String DB_CLIENT_HOST_SPLIT_BY_INSTANCE = "trace.db.client.split-by-instance";
  public static final String PARTIAL_FLUSH_MIN_SPANS = "trace.partial.flush.min.spans";
  public static final String RUNTIME_CONTEXT_FIELD_INJECTION =
      "trace.runtime.context.field.injection";
  public static final String PROPAGATION_STYLE_EXTRACT = "propagation.style.extract";
  public static final String PROPAGATION_STYLE_INJECT = "propagation.style.inject";

  public static final String JMX_FETCH_ENABLED = "jmxfetch.enabled";
  public static final String JMX_FETCH_CONFIG_DIR = "jmxfetch.config.dir";
  public static final String JMX_FETCH_CONFIG = "jmxfetch.config";
  public static final String JMX_FETCH_METRICS_CONFIGS = "jmxfetch.metrics-configs";
  public static final String JMX_FETCH_CHECK_PERIOD = "jmxfetch.check-period";
  public static final String JMX_FETCH_REFRESH_BEANS_PERIOD = "jmxfetch.refresh-beans-period";
  public static final String JMX_FETCH_STATSD_HOST = "jmxfetch.statsd.host";
  public static final String JMX_FETCH_STATSD_PORT = "jmxfetch.statsd.port";

  public static final String LOGS_INJECTION_ENABLED = "logs.injection";

  public static final String RUNTIME_ID_TAG = "runtime-id";
  public static final String LANGUAGE_TAG_KEY = "language";
  public static final String LANGUAGE_TAG_VALUE = "jvm";

  public static final String DEFAULT_SERVICE_NAME = "unnamed-java-app";

  private static final boolean DEFAULT_TRACE_ENABLED = true;
  public static final boolean DEFAULT_INTEGRATIONS_ENABLED = true;
  public static final String DD_AGENT_WRITER_TYPE = "DDAgentWriter";
  public static final String LOGGING_WRITER_TYPE = "LoggingWriter";
  private static final String DEFAULT_AGENT_WRITER_TYPE = DD_AGENT_WRITER_TYPE;

  public static final String DEFAULT_AGENT_HOST = "localhost";
  public static final int DEFAULT_TRACE_AGENT_PORT = 8126;
  public static final String DEFAULT_AGENT_UNIX_DOMAIN_SOCKET = null;

  private static final boolean DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION = true;

  private static final boolean DEFAULT_PRIORITY_SAMPLING_ENABLED = true;
  private static final boolean DEFAULT_TRACE_RESOLVER_ENABLED = true;
  private static final Set<Integer> DEFAULT_HTTP_SERVER_ERROR_STATUSES =
      parseIntegerRangeSet("500-599", "default");
  private static final Set<Integer> DEFAULT_HTTP_CLIENT_ERROR_STATUSES =
      parseIntegerRangeSet("400-499", "default");
  private static final boolean DEFAULT_HTTP_SERVER_TAG_QUERY_STRING = false;
  private static final boolean DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING = false;
  private static final boolean DEFAULT_HTTP_CLIENT_SPLIT_BY_DOMAIN = false;
  private static final boolean DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE = false;
  private static final int DEFAULT_PARTIAL_FLUSH_MIN_SPANS = 1000;
  private static final String DEFAULT_PROPAGATION_STYLE_EXTRACT = PropagationStyle.DATADOG.name();
  private static final String DEFAULT_PROPAGATION_STYLE_INJECT = PropagationStyle.DATADOG.name();
  private static final boolean DEFAULT_JMX_FETCH_ENABLED = true;

  public static final int DEFAULT_JMX_FETCH_STATSD_PORT = 8125;

  public static final boolean DEFAULT_LOGS_INJECTION_ENABLED = false;

  private static final String SPLIT_BY_SPACE_OR_COMMA_REGEX = "[,\\s]+";

  private static final boolean DEFAULT_TRACE_REPORT_HOSTNAME = false;
  private static final String DEFAULT_TRACE_ANNOTATIONS = null;
  private static final boolean DEFAULT_TRACE_EXECUTORS_ALL = false;
  private static final String DEFAULT_TRACE_EXECUTORS = "";
  private static final String DEFAULT_TRACE_METHODS = null;
  public static final boolean DEFAULT_TRACE_ANALYTICS_ENABLED = false;
  public static final float DEFAULT_ANALYTICS_SAMPLE_RATE = 1.0f;

  public enum PropagationStyle {
    DATADOG,
    B3
  }

  /** A tag intended for internal use only, hence not added to the public api DDTags class. */
  private static final String INTERNAL_HOST_NAME = "_dd.hostname";

  /**
   * this is a random UUID that gets generated on JVM start up and is attached to every root span
   * and every JMX metric that is sent out.
   */
  @Getter private final String runtimeId;

  @Getter private final String serviceName;
  @Getter private final boolean traceEnabled;
  @Getter private final boolean integrationsEnabled;
  @Getter private final String writerType;
  @Getter private final String agentHost;
  @Getter private final int agentPort;
  @Getter private final String agentUnixDomainSocket;
  @Getter private final boolean prioritySamplingEnabled;
  @Getter private final boolean traceResolverEnabled;
  @Getter private final Map<String, String> serviceMapping;
  private final Map<String, String> globalTags;
  private final Map<String, String> spanTags;
  private final Map<String, String> jmxTags;
  @Getter private final List<String> excludedClasses;
  @Getter private final Map<String, String> headerTags;
  @Getter private final Set<Integer> httpServerErrorStatuses;
  @Getter private final Set<Integer> httpClientErrorStatuses;
  @Getter private final boolean httpServerTagQueryString;
  @Getter private final boolean httpClientTagQueryString;
  @Getter private final boolean httpClientSplitByDomain;
  @Getter private final boolean dbClientSplitByInstance;
  @Getter private final Integer partialFlushMinSpans;
  @Getter private final boolean runtimeContextFieldInjection;
  @Getter private final Set<PropagationStyle> propagationStylesToExtract;
  @Getter private final Set<PropagationStyle> propagationStylesToInject;

  @Getter private final boolean jmxFetchEnabled;
  @Getter private final String jmxFetchConfigDir;
  @Getter private final List<String> jmxFetchConfigs;
  @Deprecated @Getter private final List<String> jmxFetchMetricsConfigs;
  @Getter private final Integer jmxFetchCheckPeriod;
  @Getter private final Integer jmxFetchRefreshBeansPeriod;
  @Getter private final String jmxFetchStatsdHost;
  @Getter private final Integer jmxFetchStatsdPort;

  @Getter private final boolean logsInjectionEnabled;

  @Getter private final boolean reportHostName;

  @Getter private final String traceAnnotations;

  @Getter private final String traceMethods;

  @Getter private final boolean traceExecutorsAll;
  @Getter private final List<String> traceExecutors;

  @Getter private final boolean traceAnalyticsEnabled;

  // Values from an optionally provided properties file
  private static Properties propertiesFromConfigFile;

  // Read order: System Properties -> Env Variables, [-> properties file], [-> default value]
  // Visible for testing
  Config() {
    propertiesFromConfigFile = loadConfigurationFile();

    runtimeId = UUID.randomUUID().toString();

    serviceName = getSettingFromEnvironment(SERVICE_NAME, DEFAULT_SERVICE_NAME);

    traceEnabled = getBooleanSettingFromEnvironment(TRACE_ENABLED, DEFAULT_TRACE_ENABLED);
    integrationsEnabled =
        getBooleanSettingFromEnvironment(INTEGRATIONS_ENABLED, DEFAULT_INTEGRATIONS_ENABLED);
    writerType = getSettingFromEnvironment(WRITER_TYPE, DEFAULT_AGENT_WRITER_TYPE);
    agentHost = getSettingFromEnvironment(AGENT_HOST, DEFAULT_AGENT_HOST);
    agentPort =
        getIntegerSettingFromEnvironment(
            TRACE_AGENT_PORT,
            getIntegerSettingFromEnvironment(AGENT_PORT_LEGACY, DEFAULT_TRACE_AGENT_PORT));
    agentUnixDomainSocket =
        getSettingFromEnvironment(AGENT_UNIX_DOMAIN_SOCKET, DEFAULT_AGENT_UNIX_DOMAIN_SOCKET);
    prioritySamplingEnabled =
        getBooleanSettingFromEnvironment(PRIORITY_SAMPLING, DEFAULT_PRIORITY_SAMPLING_ENABLED);
    traceResolverEnabled =
        getBooleanSettingFromEnvironment(TRACE_RESOLVER_ENABLED, DEFAULT_TRACE_RESOLVER_ENABLED);
    serviceMapping = getMapSettingFromEnvironment(SERVICE_MAPPING, null);

    globalTags = getMapSettingFromEnvironment(GLOBAL_TAGS, null);
    spanTags = getMapSettingFromEnvironment(SPAN_TAGS, null);
    jmxTags = getMapSettingFromEnvironment(JMX_TAGS, null);

    excludedClasses = getListSettingFromEnvironment(TRACE_CLASSES_EXCLUDE, null);
    headerTags = getMapSettingFromEnvironment(HEADER_TAGS, null);

    httpServerErrorStatuses =
        getIntegerRangeSettingFromEnvironment(
            HTTP_SERVER_ERROR_STATUSES, DEFAULT_HTTP_SERVER_ERROR_STATUSES);

    httpClientErrorStatuses =
        getIntegerRangeSettingFromEnvironment(
            HTTP_CLIENT_ERROR_STATUSES, DEFAULT_HTTP_CLIENT_ERROR_STATUSES);

    httpServerTagQueryString =
        getBooleanSettingFromEnvironment(
            HTTP_SERVER_TAG_QUERY_STRING, DEFAULT_HTTP_SERVER_TAG_QUERY_STRING);

    httpClientTagQueryString =
        getBooleanSettingFromEnvironment(
            HTTP_CLIENT_TAG_QUERY_STRING, DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING);

    httpClientSplitByDomain =
        getBooleanSettingFromEnvironment(
            HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, DEFAULT_HTTP_CLIENT_SPLIT_BY_DOMAIN);

    dbClientSplitByInstance =
        getBooleanSettingFromEnvironment(
            DB_CLIENT_HOST_SPLIT_BY_INSTANCE, DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE);

    partialFlushMinSpans =
        getIntegerSettingFromEnvironment(PARTIAL_FLUSH_MIN_SPANS, DEFAULT_PARTIAL_FLUSH_MIN_SPANS);

    runtimeContextFieldInjection =
        getBooleanSettingFromEnvironment(
            RUNTIME_CONTEXT_FIELD_INJECTION, DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION);

    propagationStylesToExtract =
        getEnumSetSettingFromEnvironment(
            PROPAGATION_STYLE_EXTRACT,
            DEFAULT_PROPAGATION_STYLE_EXTRACT,
            PropagationStyle.class,
            true);
    propagationStylesToInject =
        getEnumSetSettingFromEnvironment(
            PROPAGATION_STYLE_INJECT,
            DEFAULT_PROPAGATION_STYLE_INJECT,
            PropagationStyle.class,
            true);

    jmxFetchEnabled =
        getBooleanSettingFromEnvironment(JMX_FETCH_ENABLED, DEFAULT_JMX_FETCH_ENABLED);
    jmxFetchConfigDir = getSettingFromEnvironment(JMX_FETCH_CONFIG_DIR, null);
    jmxFetchConfigs = getListSettingFromEnvironment(JMX_FETCH_CONFIG, null);
    jmxFetchMetricsConfigs = getListSettingFromEnvironment(JMX_FETCH_METRICS_CONFIGS, null);
    jmxFetchCheckPeriod = getIntegerSettingFromEnvironment(JMX_FETCH_CHECK_PERIOD, null);
    jmxFetchRefreshBeansPeriod =
        getIntegerSettingFromEnvironment(JMX_FETCH_REFRESH_BEANS_PERIOD, null);
    jmxFetchStatsdHost = getSettingFromEnvironment(JMX_FETCH_STATSD_HOST, null);
    jmxFetchStatsdPort =
        getIntegerSettingFromEnvironment(JMX_FETCH_STATSD_PORT, DEFAULT_JMX_FETCH_STATSD_PORT);

    logsInjectionEnabled =
        getBooleanSettingFromEnvironment(LOGS_INJECTION_ENABLED, DEFAULT_LOGS_INJECTION_ENABLED);

    reportHostName =
        getBooleanSettingFromEnvironment(TRACE_REPORT_HOSTNAME, DEFAULT_TRACE_REPORT_HOSTNAME);

    traceAnnotations = getSettingFromEnvironment(TRACE_ANNOTATIONS, DEFAULT_TRACE_ANNOTATIONS);

    traceMethods = getSettingFromEnvironment(TRACE_METHODS, DEFAULT_TRACE_METHODS);

    traceExecutorsAll =
        getBooleanSettingFromEnvironment(TRACE_EXECUTORS_ALL, DEFAULT_TRACE_EXECUTORS_ALL);

    traceExecutors = getListSettingFromEnvironment(TRACE_EXECUTORS, DEFAULT_TRACE_EXECUTORS);

    traceAnalyticsEnabled =
        getBooleanSettingFromEnvironment(TRACE_ANALYTICS_ENABLED, DEFAULT_TRACE_ANALYTICS_ENABLED);

    log.debug("New instance: {}", this);
  }

  // Read order: Properties -> Parent
  private Config(final Properties properties, final Config parent) {
    runtimeId = parent.runtimeId;

    serviceName = properties.getProperty(SERVICE_NAME, parent.serviceName);

    traceEnabled = getPropertyBooleanValue(properties, TRACE_ENABLED, parent.traceEnabled);
    integrationsEnabled =
        getPropertyBooleanValue(properties, INTEGRATIONS_ENABLED, parent.integrationsEnabled);
    writerType = properties.getProperty(WRITER_TYPE, parent.writerType);
    agentHost = properties.getProperty(AGENT_HOST, parent.agentHost);
    agentPort =
        getPropertyIntegerValue(
            properties,
            TRACE_AGENT_PORT,
            getPropertyIntegerValue(properties, AGENT_PORT_LEGACY, parent.agentPort));
    agentUnixDomainSocket =
        properties.getProperty(AGENT_UNIX_DOMAIN_SOCKET, parent.agentUnixDomainSocket);
    prioritySamplingEnabled =
        getPropertyBooleanValue(properties, PRIORITY_SAMPLING, parent.prioritySamplingEnabled);
    traceResolverEnabled =
        getPropertyBooleanValue(properties, TRACE_RESOLVER_ENABLED, parent.traceResolverEnabled);
    serviceMapping = getPropertyMapValue(properties, SERVICE_MAPPING, parent.serviceMapping);

    globalTags = getPropertyMapValue(properties, GLOBAL_TAGS, parent.globalTags);
    spanTags = getPropertyMapValue(properties, SPAN_TAGS, parent.spanTags);
    jmxTags = getPropertyMapValue(properties, JMX_TAGS, parent.jmxTags);
    excludedClasses =
        getPropertyListValue(properties, TRACE_CLASSES_EXCLUDE, parent.excludedClasses);
    headerTags = getPropertyMapValue(properties, HEADER_TAGS, parent.headerTags);

    httpServerErrorStatuses =
        getPropertyIntegerRangeValue(
            properties, HTTP_SERVER_ERROR_STATUSES, parent.httpServerErrorStatuses);

    httpClientErrorStatuses =
        getPropertyIntegerRangeValue(
            properties, HTTP_CLIENT_ERROR_STATUSES, parent.httpClientErrorStatuses);

    httpServerTagQueryString =
        getPropertyBooleanValue(
            properties, HTTP_SERVER_TAG_QUERY_STRING, parent.httpServerTagQueryString);

    httpClientTagQueryString =
        getPropertyBooleanValue(
            properties, HTTP_CLIENT_TAG_QUERY_STRING, parent.httpClientTagQueryString);

    httpClientSplitByDomain =
        getPropertyBooleanValue(
            properties, HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, parent.httpClientSplitByDomain);

    dbClientSplitByInstance =
        getPropertyBooleanValue(
            properties, DB_CLIENT_HOST_SPLIT_BY_INSTANCE, parent.dbClientSplitByInstance);

    partialFlushMinSpans =
        getPropertyIntegerValue(properties, PARTIAL_FLUSH_MIN_SPANS, parent.partialFlushMinSpans);

    runtimeContextFieldInjection =
        getPropertyBooleanValue(
            properties, RUNTIME_CONTEXT_FIELD_INJECTION, parent.runtimeContextFieldInjection);

    final Set<PropagationStyle> parsedPropagationStylesToExtract =
        getPropertySetValue(properties, PROPAGATION_STYLE_EXTRACT, PropagationStyle.class);
    propagationStylesToExtract =
        parsedPropagationStylesToExtract == null
            ? parent.propagationStylesToExtract
            : parsedPropagationStylesToExtract;
    final Set<PropagationStyle> parsedPropagationStylesToInject =
        getPropertySetValue(properties, PROPAGATION_STYLE_INJECT, PropagationStyle.class);
    propagationStylesToInject =
        parsedPropagationStylesToInject == null
            ? parent.propagationStylesToInject
            : parsedPropagationStylesToInject;

    jmxFetchEnabled =
        getPropertyBooleanValue(properties, JMX_FETCH_ENABLED, parent.jmxFetchEnabled);
    jmxFetchConfigDir = properties.getProperty(JMX_FETCH_CONFIG_DIR, parent.jmxFetchConfigDir);
    jmxFetchConfigs = getPropertyListValue(properties, JMX_FETCH_CONFIG, parent.jmxFetchConfigs);
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

    logsInjectionEnabled =
        getBooleanSettingFromEnvironment(LOGS_INJECTION_ENABLED, DEFAULT_LOGS_INJECTION_ENABLED);

    reportHostName =
        getPropertyBooleanValue(properties, TRACE_REPORT_HOSTNAME, parent.reportHostName);

    traceAnnotations = properties.getProperty(TRACE_ANNOTATIONS, parent.traceAnnotations);

    traceMethods = properties.getProperty(TRACE_METHODS, parent.traceMethods);

    traceExecutorsAll =
        getPropertyBooleanValue(properties, TRACE_EXECUTORS_ALL, parent.traceExecutorsAll);
    traceExecutors = getPropertyListValue(properties, TRACE_EXECUTORS, parent.traceExecutors);

    traceAnalyticsEnabled =
        getPropertyBooleanValue(properties, TRACE_ANALYTICS_ENABLED, parent.traceAnalyticsEnabled);

    log.debug("New instance: {}", this);
  }

  /** @return A map of tags to be applied only to the local application root span. */
  public Map<String, String> getLocalRootSpanTags() {
    final Map<String, String> runtimeTags = getRuntimeTags();
    final Map<String, String> result = new HashMap<>(runtimeTags);

    if (reportHostName) {
      final String hostName = getHostName();
      if (null != hostName && !hostName.isEmpty()) {
        result.put(INTERNAL_HOST_NAME, hostName);
      }
    }

    return Collections.unmodifiableMap(result);
  }

  public Map<String, String> getMergedSpanTags() {
    // DO not include runtimeId into span tags: we only want that added to the root span
    final Map<String, String> result = newHashMap(globalTags.size() + spanTags.size());
    result.putAll(globalTags);
    result.putAll(spanTags);
    return Collections.unmodifiableMap(result);
  }

  public Map<String, String> getMergedJmxTags() {
    final Map<String, String> runtimeTags = getRuntimeTags();
    final Map<String, String> result =
        newHashMap(
            globalTags.size() + jmxTags.size() + runtimeTags.size() + 1 /* for serviceName */);
    result.putAll(globalTags);
    result.putAll(jmxTags);
    result.putAll(runtimeTags);
    // service name set here instead of getRuntimeTags because apm already manages the service tag
    // and may chose to override it.
    // Additionally, infra/JMX metrics require `service` rather than APM's `service.name` tag
    result.put(SERVICE, serviceName);
    return Collections.unmodifiableMap(result);
  }

  /**
   * Returns the sample rate for the specified instrumentation or {@link
   * #DEFAULT_ANALYTICS_SAMPLE_RATE} if none specified.
   */
  public float getInstrumentationAnalyticsSampleRate(String... aliases) {
    for (final String alias : aliases) {
      Float rate = getFloatSettingFromEnvironment(alias + ".analytics.sample-rate", null);
      if (null != rate) {
        return rate;
      }
    }
    return DEFAULT_ANALYTICS_SAMPLE_RATE;
  }

  /**
   * Return a map of tags required by the datadog backend to link runtime metrics (i.e. jmx) and
   * traces.
   *
   * <p>These tags must be applied to every runtime metrics and placed on the root span of every
   * trace.
   *
   * @return A map of tag-name -> tag-value
   */
  private Map<String, String> getRuntimeTags() {
    final Map<String, String> result = newHashMap(2);
    result.put(RUNTIME_ID_TAG, runtimeId);
    result.put(LANGUAGE_TAG_KEY, LANGUAGE_TAG_VALUE);
    return Collections.unmodifiableMap(result);
  }

  public boolean isIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    return integrationEnabled(integrationNames, defaultEnabled);
  }

  /**
   * @deprecated This method should only be used internally. Use the instance getter instead {@link
   *     #isIntegrationEnabled(SortedSet, boolean)}.
   * @param integrationNames
   * @param defaultEnabled
   * @return
   */
  public static boolean integrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (final String name : integrationNames) {
      final boolean configEnabled =
          getBooleanSettingFromEnvironment("integration." + name + ".enabled", defaultEnabled);
      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  public boolean isJmxFetchIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    return jmxFetchIntegrationEnabled(integrationNames, defaultEnabled);
  }

  /**
   * @deprecated This method should only be used internally. Use the instance getter instead {@link
   *     #isJmxFetchIntegrationEnabled(SortedSet, boolean)}.
   * @param integrationNames
   * @param defaultEnabled
   * @return
   */
  public static boolean jmxFetchIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (final String name : integrationNames) {
      final boolean configEnabled =
          getBooleanSettingFromEnvironment("jmxfetch." + name + ".enabled", defaultEnabled);
      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  public boolean isTraceAnalyticsIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    return traceAnalyticsIntegrationEnabled(integrationNames, defaultEnabled);
  }

  /**
   * @deprecated This method should only be used internally. Use the instance getter instead {@link
   *     #isTraceAnalyticsIntegrationEnabled(SortedSet, boolean)}.
   * @param integrationNames
   * @param defaultEnabled
   * @return
   */
  public static boolean traceAnalyticsIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (final String name : integrationNames) {
      final boolean configEnabled =
          getBooleanSettingFromEnvironment(name + ".analytics.enabled", defaultEnabled);
      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  /**
   * Helper method that takes the name, adds a "dd." prefix then checks for System Properties of
   * that name. If none found, the name is converted to an Environment Variable and used to check
   * the env. If none of the above returns a value, then an optional properties file if checked. If
   * setting is not configured in either location, <code>defaultValue</code> is returned.
   *
   * @param name
   * @param defaultValue
   * @return
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static String getSettingFromEnvironment(final String name, final String defaultValue) {
    String value;

    // System properties and properties provided from command line have the highest precedence
    value = System.getProperties().getProperty(propertyNameToSystemPropertyName(name));
    if (null != value) {
      return value;
    }

    // If value not provided from system properties, looking at env variables
    value = System.getenv(propertyNameToEnvironmentVariableName(name));
    if (null != value) {
      return value;
    }

    // If value is not defined yet, we look at properties optionally defined in a properties file
    value = propertiesFromConfigFile.getProperty(propertyNameToSystemPropertyName(name));
    if (null != value) {
      return value;
    }

    return defaultValue;
  }

  /** @deprecated This method should only be used internally. Use the explicit getter instead. */
  private static Map<String, String> getMapSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseMap(
        getSettingFromEnvironment(name, defaultValue), propertyNameToSystemPropertyName(name));
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a list by
   * splitting on `,`.
   *
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static List<String> getListSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseList(getSettingFromEnvironment(name, defaultValue));
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Boolean.
   *
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static Boolean getBooleanSettingFromEnvironment(
      final String name, final Boolean defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    return value == null || value.trim().isEmpty() ? defaultValue : Boolean.valueOf(value);
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Float.
   *
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static Float getFloatSettingFromEnvironment(final String name, final Float defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    try {
      return value == null ? defaultValue : Float.valueOf(value);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Integer.
   */
  private static Integer getIntegerSettingFromEnvironment(
      final String name, final Integer defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    try {
      return value == null ? defaultValue : Integer.valueOf(value);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a set of
   * strings splitting by space or comma.
   */
  private static <T extends Enum<T>> Set<T> getEnumSetSettingFromEnvironment(
      final String name,
      final String defaultValue,
      final Class<T> clazz,
      final boolean emptyResultMeansUseDefault) {
    final String value = getSettingFromEnvironment(name, defaultValue);
    Set<T> result =
        convertStringSetToEnumSet(
            parseStringIntoSetOfNonEmptyStrings(value, SPLIT_BY_SPACE_OR_COMMA_REGEX), clazz);

    if (emptyResultMeansUseDefault && result.isEmpty()) {
      // Treat empty parsing result as no value and use default instead
      result =
          convertStringSetToEnumSet(
              parseStringIntoSetOfNonEmptyStrings(defaultValue, SPLIT_BY_SPACE_OR_COMMA_REGEX),
              clazz);
    }

    return result;
  }

  private Set<Integer> getIntegerRangeSettingFromEnvironment(
      final String name, final Set<Integer> defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    try {
      return value == null ? defaultValue : parseIntegerRangeSet(value, name);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  /**
   * Converts the property name, e.g. 'service.name' into a public environment variable name, e.g.
   * `DD_SERVICE_NAME`.
   *
   * @param setting The setting name, e.g. `service.name`
   * @return The public facing environment variable name
   */
  private static String propertyNameToEnvironmentVariableName(final String setting) {
    return ENV_REPLACEMENT
        .matcher(propertyNameToSystemPropertyName(setting).toUpperCase())
        .replaceAll("_");
  }

  /**
   * Converts the property name, e.g. 'service.name' into a public system property name, e.g.
   * `dd.service.name`.
   *
   * @param setting The setting name, e.g. `service.name`
   * @return The public facing system property name
   */
  private static String propertyNameToSystemPropertyName(String setting) {
    return PREFIX + setting;
  }

  private static Map<String, String> getPropertyMapValue(
      final Properties properties, final String name, final Map<String, String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : parseMap(value, name);
  }

  private static List<String> getPropertyListValue(
      final Properties properties, final String name, final List<String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : parseList(value);
  }

  private static Boolean getPropertyBooleanValue(
      final Properties properties, final String name, final Boolean defaultValue) {
    final String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : Boolean.valueOf(value);
  }

  private static Integer getPropertyIntegerValue(
      final Properties properties, final String name, final Integer defaultValue) {
    final String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : Integer.valueOf(value);
  }

  private static <T extends Enum<T>> Set<T> getPropertySetValue(
      final Properties properties, final String name, final Class<T> clazz) {
    final String value = properties.getProperty(name);
    if (value != null) {
      final Set<T> result =
          convertStringSetToEnumSet(
              parseStringIntoSetOfNonEmptyStrings(value, SPLIT_BY_SPACE_OR_COMMA_REGEX), clazz);
      if (!result.isEmpty()) {
        return result;
      }
    }
    // null means parent value should be used
    return null;
  }

  private Set<Integer> getPropertyIntegerRangeValue(
      final Properties properties, final String name, final Set<Integer> defaultValue) {
    final String value = properties.getProperty(name);
    try {
      return value == null ? defaultValue : parseIntegerRangeSet(value, name);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  private static Map<String, String> parseMap(final String str, final String settingName) {
    // If we ever want to have default values besides an empty map, this will need to change.
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

  private static Set<Integer> parseIntegerRangeSet(String str, final String settingName)
      throws NumberFormatException {
    assert str != null;
    str = str.replaceAll("\\s", "");
    if (!str.matches("\\d{3}(?:-\\d{3})?(?:,\\d{3}(?:-\\d{3})?)*")) {
      log.warn(
          "Invalid config for {}: '{}'. Must be formatted like '400-403,405,410-499'.",
          settingName,
          str);
      throw new NumberFormatException();
    }

    final String[] tokens = str.split(",", -1);
    final Set<Integer> set = new HashSet<>();

    for (final String token : tokens) {
      final String[] range = token.split("-", -1);
      if (range.length == 1) {
        set.add(Integer.parseInt(range[0]));
      } else if (range.length == 2) {
        final int left = Integer.parseInt(range[0]);
        final int right = Integer.parseInt(range[1]);
        final int min = Math.min(left, right);
        final int max = Math.max(left, right);
        for (int i = min; i <= max; i++) {
          set.add(i);
        }
      }
    }
    return Collections.unmodifiableSet(set);
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

  private static Set<String> parseStringIntoSetOfNonEmptyStrings(
      final String str, final String regex) {
    // Using LinkedHashSet to preserve original string order
    final Set<String> result = new LinkedHashSet<>();
    // Java returns single value when splitting an empty string. We do not need that value, so
    // we need to throw it out.
    for (final String value : str.split(regex)) {
      if (!value.isEmpty()) {
        result.add(value);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  private static <V extends Enum<V>> Set<V> convertStringSetToEnumSet(
      final Set<String> input, final Class<V> clazz) {
    // Using LinkedHashSet to preserve original string order
    final Set<V> result = new LinkedHashSet<>();
    for (final String value : input) {
      try {
        result.add(Enum.valueOf(clazz, value.toUpperCase()));
      } catch (final IllegalArgumentException e) {
        log.debug("Cannot recognize config string value: {}, {}", value, clazz);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   */
  private static Properties loadConfigurationFile() {
    Properties properties = new Properties();

    // Reading from system property first and from env after
    String configurationFilePath =
        System.getProperty(propertyNameToSystemPropertyName(CONFIGURATION_FILE));
    if (null == configurationFilePath) {
      configurationFilePath =
          System.getenv(propertyNameToEnvironmentVariableName(CONFIGURATION_FILE));
    }
    if (null == configurationFilePath) {
      return properties;
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
      return properties;
    }

    try {
      FileReader fileReader = new FileReader(configurationFile);
      properties.load(fileReader);
    } catch (FileNotFoundException fnf) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
    } catch (IOException ioe) {
      log.error(
          "Configuration file '{}' cannot be accessed or correctly parsed.", configurationFilePath);
    }

    return properties;
  }

  /**
   * Returns the detected hostname. This operation is time consuming so if the usage changes and
   * this method will be called several times then we should implement some sort of caching.
   */
  private String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException e) {
      // If we are not able to detect the hostname we do not throw an exception.
    }

    return null;
  }

  // This has to be placed after all other static fields to give them a chance to initialize
  private static final Config INSTANCE = new Config();

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
