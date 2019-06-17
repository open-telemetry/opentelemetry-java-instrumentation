package datadog.trace.api

import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

import static datadog.trace.api.Config.AGENT_HOST
import static datadog.trace.api.Config.AGENT_PORT_LEGACY
import static datadog.trace.api.Config.AGENT_UNIX_DOMAIN_SOCKET
import static datadog.trace.api.Config.CONFIGURATION_FILE
import static datadog.trace.api.Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE
import static datadog.trace.api.Config.DEFAULT_JMX_FETCH_STATSD_PORT
import static datadog.trace.api.Config.GLOBAL_TAGS
import static datadog.trace.api.Config.HEADER_TAGS
import static datadog.trace.api.Config.HTTP_CLIENT_ERROR_STATUSES
import static datadog.trace.api.Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN
import static datadog.trace.api.Config.HTTP_SERVER_ERROR_STATUSES
import static datadog.trace.api.Config.JMX_FETCH_CHECK_PERIOD
import static datadog.trace.api.Config.JMX_FETCH_ENABLED
import static datadog.trace.api.Config.JMX_FETCH_METRICS_CONFIGS
import static datadog.trace.api.Config.JMX_FETCH_REFRESH_BEANS_PERIOD
import static datadog.trace.api.Config.JMX_FETCH_STATSD_HOST
import static datadog.trace.api.Config.JMX_FETCH_STATSD_PORT
import static datadog.trace.api.Config.JMX_TAGS
import static datadog.trace.api.Config.LANGUAGE_TAG_KEY
import static datadog.trace.api.Config.LANGUAGE_TAG_VALUE
import static datadog.trace.api.Config.PARTIAL_FLUSH_MIN_SPANS
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.PRIORITY_SAMPLING
import static datadog.trace.api.Config.PROPAGATION_STYLE_EXTRACT
import static datadog.trace.api.Config.PROPAGATION_STYLE_INJECT
import static datadog.trace.api.Config.RUNTIME_CONTEXT_FIELD_INJECTION
import static datadog.trace.api.Config.RUNTIME_ID_TAG
import static datadog.trace.api.Config.SERVICE
import static datadog.trace.api.Config.SERVICE_MAPPING
import static datadog.trace.api.Config.SERVICE_NAME
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.TRACE_AGENT_PORT
import static datadog.trace.api.Config.TRACE_ENABLED
import static datadog.trace.api.Config.TRACE_REPORT_HOSTNAME
import static datadog.trace.api.Config.TRACE_RESOLVER_ENABLED
import static datadog.trace.api.Config.WRITER_TYPE

class ConfigTest extends Specification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  private static final DD_SERVICE_NAME_ENV = "DD_SERVICE_NAME"
  private static final DD_TRACE_ENABLED_ENV = "DD_TRACE_ENABLED"
  private static final DD_WRITER_TYPE_ENV = "DD_WRITER_TYPE"
  private static final DD_SERVICE_MAPPING_ENV = "DD_SERVICE_MAPPING"
  private static final DD_SPAN_TAGS_ENV = "DD_SPAN_TAGS"
  private static final DD_HEADER_TAGS_ENV = "DD_HEADER_TAGS"
  private static final DD_PROPAGATION_STYLE_EXTRACT = "DD_PROPAGATION_STYLE_EXTRACT"
  private static final DD_PROPAGATION_STYLE_INJECT = "DD_PROPAGATION_STYLE_INJECT"
  private static final DD_JMXFETCH_METRICS_CONFIGS_ENV = "DD_JMXFETCH_METRICS_CONFIGS"
  private static final DD_TRACE_AGENT_PORT_ENV = "DD_TRACE_AGENT_PORT"
  private static final DD_AGENT_PORT_LEGACY_ENV = "DD_AGENT_PORT"
  private static final DD_TRACE_REPORT_HOSTNAME = "DD_TRACE_REPORT_HOSTNAME"

  def "verify defaults"() {
    when:
    Config config = provider()

    then:
    config.serviceName == "unnamed-java-app"
    config.traceEnabled == true
    config.writerType == "DDAgentWriter"
    config.agentHost == "localhost"
    config.agentPort == 8126
    config.agentUnixDomainSocket == null
    config.prioritySamplingEnabled == true
    config.traceResolverEnabled == true
    config.serviceMapping == [:]
    config.mergedSpanTags == [:]
    config.mergedJmxTags == [(RUNTIME_ID_TAG): config.getRuntimeId(), (SERVICE): config.serviceName, (LANGUAGE_TAG_KEY): LANGUAGE_TAG_VALUE]
    config.headerTags == [:]
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..499).toSet()
    config.httpClientSplitByDomain == false
    config.dbClientSplitByInstance == false
    config.partialFlushMinSpans == 1000
    config.reportHostName == false
    config.runtimeContextFieldInjection == true
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.DATADOG]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.DATADOG]
    config.jmxFetchEnabled == true
    config.jmxFetchMetricsConfigs == []
    config.jmxFetchCheckPeriod == null
    config.jmxFetchRefreshBeansPeriod == null
    config.jmxFetchStatsdHost == null
    config.jmxFetchStatsdPort == DEFAULT_JMX_FETCH_STATSD_PORT
    config.toString().contains("unnamed-java-app")

    where:
    provider << [{ new Config() }, { Config.get() }, {
      def props = new Properties()
      props.setProperty("something", "unused")
      Config.get(props)
    }]
  }

  def "specify overrides via properties"() {
    setup:
    def prop = new Properties()
    prop.setProperty(SERVICE_NAME, "something else")
    prop.setProperty(TRACE_ENABLED, "false")
    prop.setProperty(WRITER_TYPE, "LoggingWriter")
    prop.setProperty(AGENT_HOST, "somehost")
    prop.setProperty(TRACE_AGENT_PORT, "123")
    prop.setProperty(AGENT_UNIX_DOMAIN_SOCKET, "somepath")
    prop.setProperty(AGENT_PORT_LEGACY, "456")
    prop.setProperty(PRIORITY_SAMPLING, "false")
    prop.setProperty(TRACE_RESOLVER_ENABLED, "false")
    prop.setProperty(SERVICE_MAPPING, "a:1")
    prop.setProperty(GLOBAL_TAGS, "b:2")
    prop.setProperty(SPAN_TAGS, "c:3")
    prop.setProperty(JMX_TAGS, "d:4")
    prop.setProperty(HEADER_TAGS, "e:5")
    prop.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    prop.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    prop.setProperty(HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    prop.setProperty(DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    prop.setProperty(PARTIAL_FLUSH_MIN_SPANS, "15")
    prop.setProperty(TRACE_REPORT_HOSTNAME, "true")
    prop.setProperty(RUNTIME_CONTEXT_FIELD_INJECTION, "false")
    prop.setProperty(PROPAGATION_STYLE_EXTRACT, "Datadog, B3")
    prop.setProperty(PROPAGATION_STYLE_INJECT, "B3, Datadog")
    prop.setProperty(JMX_FETCH_ENABLED, "false")
    prop.setProperty(JMX_FETCH_METRICS_CONFIGS, "/foo.yaml,/bar.yaml")
    prop.setProperty(JMX_FETCH_CHECK_PERIOD, "100")
    prop.setProperty(JMX_FETCH_REFRESH_BEANS_PERIOD, "200")
    prop.setProperty(JMX_FETCH_STATSD_HOST, "statsd host")
    prop.setProperty(JMX_FETCH_STATSD_PORT, "321")

    when:
    Config config = Config.get(prop)

    then:
    config.serviceName == "something else"
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.agentHost == "somehost"
    config.agentPort == 123
    config.agentUnixDomainSocket == "somepath"
    config.prioritySamplingEnabled == false
    config.traceResolverEnabled == false
    config.serviceMapping == [a: "1"]
    config.mergedSpanTags == [b: "2", c: "3"]
    config.mergedJmxTags == [b: "2", d: "4", (RUNTIME_ID_TAG): config.getRuntimeId(), (SERVICE): config.serviceName, (LANGUAGE_TAG_KEY): LANGUAGE_TAG_VALUE]
    config.headerTags == [e: "5"]
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 15
    config.reportHostName == true
    config.runtimeContextFieldInjection == false
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.DATADOG, Config.PropagationStyle.B3]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.B3, Config.PropagationStyle.DATADOG]
    config.jmxFetchEnabled == false
    config.jmxFetchMetricsConfigs == ["/foo.yaml", "/bar.yaml"]
    config.jmxFetchCheckPeriod == 100
    config.jmxFetchRefreshBeansPeriod == 200
    config.jmxFetchStatsdHost == "statsd host"
    config.jmxFetchStatsdPort == 321
  }

  def "specify overrides via system properties"() {
    setup:
    System.setProperty(PREFIX + SERVICE_NAME, "something else")
    System.setProperty(PREFIX + TRACE_ENABLED, "false")
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")
    System.setProperty(PREFIX + AGENT_HOST, "somehost")
    System.setProperty(PREFIX + TRACE_AGENT_PORT, "123")
    System.setProperty(PREFIX + AGENT_UNIX_DOMAIN_SOCKET, "somepath")
    System.setProperty(PREFIX + AGENT_PORT_LEGACY, "456")
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "false")
    System.setProperty(PREFIX + TRACE_RESOLVER_ENABLED, "false")
    System.setProperty(PREFIX + SERVICE_MAPPING, "a:1")
    System.setProperty(PREFIX + GLOBAL_TAGS, "b:2")
    System.setProperty(PREFIX + SPAN_TAGS, "c:3")
    System.setProperty(PREFIX + JMX_TAGS, "d:4")
    System.setProperty(PREFIX + HEADER_TAGS, "e:5")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "111")
    System.setProperty(PREFIX + HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    System.setProperty(PREFIX + DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    System.setProperty(PREFIX + PARTIAL_FLUSH_MIN_SPANS, "25")
    System.setProperty(PREFIX + TRACE_REPORT_HOSTNAME, "true")
    System.setProperty(PREFIX + RUNTIME_CONTEXT_FIELD_INJECTION, "false")
    System.setProperty(PREFIX + PROPAGATION_STYLE_EXTRACT, "Datadog, B3")
    System.setProperty(PREFIX + PROPAGATION_STYLE_INJECT, "B3, Datadog")
    System.setProperty(PREFIX + JMX_FETCH_ENABLED, "false")
    System.setProperty(PREFIX + JMX_FETCH_METRICS_CONFIGS, "/foo.yaml,/bar.yaml")
    System.setProperty(PREFIX + JMX_FETCH_CHECK_PERIOD, "100")
    System.setProperty(PREFIX + JMX_FETCH_REFRESH_BEANS_PERIOD, "200")
    System.setProperty(PREFIX + JMX_FETCH_STATSD_HOST, "statsd host")
    System.setProperty(PREFIX + JMX_FETCH_STATSD_PORT, "321")

    when:
    Config config = new Config()

    then:
    config.serviceName == "something else"
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.agentHost == "somehost"
    config.agentPort == 123
    config.agentUnixDomainSocket == "somepath"
    config.prioritySamplingEnabled == false
    config.traceResolverEnabled == false
    config.serviceMapping == [a: "1"]
    config.mergedSpanTags == [b: "2", c: "3"]
    config.mergedJmxTags == [b: "2", d: "4", (RUNTIME_ID_TAG): config.getRuntimeId(), (SERVICE): config.serviceName, (LANGUAGE_TAG_KEY): LANGUAGE_TAG_VALUE]
    config.headerTags == [e: "5"]
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 25
    config.reportHostName == true
    config.runtimeContextFieldInjection == false
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.DATADOG, Config.PropagationStyle.B3]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.B3, Config.PropagationStyle.DATADOG]
    config.jmxFetchEnabled == false
    config.jmxFetchMetricsConfigs == ["/foo.yaml", "/bar.yaml"]
    config.jmxFetchCheckPeriod == 100
    config.jmxFetchRefreshBeansPeriod == 200
    config.jmxFetchStatsdHost == "statsd host"
    config.jmxFetchStatsdPort == 321
  }

  def "specify overrides via env vars"() {
    setup:
    environmentVariables.set(DD_SERVICE_NAME_ENV, "still something else")
    environmentVariables.set(DD_TRACE_ENABLED_ENV, "false")
    environmentVariables.set(DD_WRITER_TYPE_ENV, "LoggingWriter")
    environmentVariables.set(DD_PROPAGATION_STYLE_EXTRACT, "B3 Datadog")
    environmentVariables.set(DD_PROPAGATION_STYLE_INJECT, "Datadog B3")
    environmentVariables.set(DD_JMXFETCH_METRICS_CONFIGS_ENV, "some/file")
    environmentVariables.set(DD_TRACE_REPORT_HOSTNAME, "true")

    when:
    def config = new Config()

    then:
    config.serviceName == "still something else"
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.B3, Config.PropagationStyle.DATADOG]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.DATADOG, Config.PropagationStyle.B3]
    config.jmxFetchMetricsConfigs == ["some/file"]
    config.reportHostName == true
  }

  def "sys props override env vars"() {
    setup:
    environmentVariables.set(DD_SERVICE_NAME_ENV, "still something else")
    environmentVariables.set(DD_WRITER_TYPE_ENV, "LoggingWriter")
    environmentVariables.set(DD_TRACE_AGENT_PORT_ENV, "777")

    System.setProperty(PREFIX + SERVICE_NAME, "what we actually want")
    System.setProperty(PREFIX + WRITER_TYPE, "DDAgentWriter")
    System.setProperty(PREFIX + AGENT_HOST, "somewhere")
    System.setProperty(PREFIX + TRACE_AGENT_PORT, "123")

    when:
    def config = new Config()

    then:
    config.serviceName == "what we actually want"
    config.writerType == "DDAgentWriter"
    config.agentHost == "somewhere"
    config.agentPort == 123
  }

  def "default when configured incorrectly"() {
    setup:
    System.setProperty(PREFIX + SERVICE_NAME, " ")
    System.setProperty(PREFIX + TRACE_ENABLED, " ")
    System.setProperty(PREFIX + WRITER_TYPE, " ")
    System.setProperty(PREFIX + AGENT_HOST, " ")
    System.setProperty(PREFIX + TRACE_AGENT_PORT, " ")
    System.setProperty(PREFIX + AGENT_PORT_LEGACY, "invalid")
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "3")
    System.setProperty(PREFIX + TRACE_RESOLVER_ENABLED, " ")
    System.setProperty(PREFIX + SERVICE_MAPPING, " ")
    System.setProperty(PREFIX + HEADER_TAGS, "1")
    System.setProperty(PREFIX + SPAN_TAGS, "invalid")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "1111")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "1:1")
    System.setProperty(PREFIX + HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "invalid")
    System.setProperty(PREFIX + DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "invalid")
    System.setProperty(PREFIX + PROPAGATION_STYLE_EXTRACT, "some garbage")
    System.setProperty(PREFIX + PROPAGATION_STYLE_INJECT, " ")

    when:
    def config = new Config()

    then:
    config.serviceName == " "
    config.traceEnabled == true
    config.writerType == " "
    config.agentHost == " "
    config.agentPort == 8126
    config.prioritySamplingEnabled == false
    config.traceResolverEnabled == true
    config.serviceMapping == [:]
    config.mergedSpanTags == [:]
    config.headerTags == [:]
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..499).toSet()
    config.httpClientSplitByDomain == false
    config.dbClientSplitByInstance == false
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.DATADOG]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.DATADOG]
  }

  def "sys props and env vars overrides for trace_agent_port and agent_port_legacy as expected"() {
    setup:
    if (overridePortEnvVar) {
      environmentVariables.set(DD_TRACE_AGENT_PORT_ENV, "777")
    }
    if (overrideLegacyPortEnvVar) {
      environmentVariables.set(DD_AGENT_PORT_LEGACY_ENV, "888")
    }

    if (overridePort) {
      System.setProperty(PREFIX + TRACE_AGENT_PORT, "123")
    }
    if (overrideLegacyPort) {
      System.setProperty(PREFIX + AGENT_PORT_LEGACY, "456")
    }

    when:
    def config = new Config()

    then:
    config.agentPort == expectedPort

    where:
    overridePort | overrideLegacyPort | overridePortEnvVar | overrideLegacyPortEnvVar | expectedPort
    true         | true               | false              | false                    | 123
    true         | false              | false              | false                    | 123
    false        | true               | false              | false                    | 456
    false        | false              | false              | false                    | 8126
    true         | true               | true               | false                    | 123
    true         | false              | true               | false                    | 123
    false        | true               | true               | false                    | 777 // env var gets picked up instead.
    false        | false              | true               | false                    | 777 // env var gets picked up instead.
    true         | true               | false              | true                     | 123
    true         | false              | false              | true                     | 123
    false        | true               | false              | true                     | 456
    false        | false              | false              | true                     | 888 // legacy env var gets picked up instead.
    true         | true               | true               | true                     | 123
    true         | false              | true               | true                     | 123
    false        | true               | true               | true                     | 777 // env var gets picked up instead.
    false        | false              | true               | true                     | 777 // env var gets picked up instead.
  }

  def "sys props override properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty(SERVICE_NAME, "something else")
    properties.setProperty(TRACE_ENABLED, "false")
    properties.setProperty(WRITER_TYPE, "LoggingWriter")
    properties.setProperty(AGENT_HOST, "somehost")
    properties.setProperty(TRACE_AGENT_PORT, "123")
    properties.setProperty(AGENT_UNIX_DOMAIN_SOCKET, "somepath")
    properties.setProperty(PRIORITY_SAMPLING, "false")
    properties.setProperty(TRACE_RESOLVER_ENABLED, "false")
    properties.setProperty(SERVICE_MAPPING, "a:1")
    properties.setProperty(GLOBAL_TAGS, "b:2")
    properties.setProperty(SPAN_TAGS, "c:3")
    properties.setProperty(JMX_TAGS, "d:4")
    properties.setProperty(HEADER_TAGS, "e:5")
    properties.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    properties.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    properties.setProperty(HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    properties.setProperty(DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    properties.setProperty(PARTIAL_FLUSH_MIN_SPANS, "15")
    properties.setProperty(PROPAGATION_STYLE_EXTRACT, "B3 Datadog")
    properties.setProperty(PROPAGATION_STYLE_INJECT, "Datadog B3")
    properties.setProperty(JMX_FETCH_METRICS_CONFIGS, "/foo.yaml,/bar.yaml")
    properties.setProperty(JMX_FETCH_CHECK_PERIOD, "100")
    properties.setProperty(JMX_FETCH_REFRESH_BEANS_PERIOD, "200")
    properties.setProperty(JMX_FETCH_STATSD_HOST, "statsd host")
    properties.setProperty(JMX_FETCH_STATSD_PORT, "321")

    when:
    def config = Config.get(properties)

    then:
    config.serviceName == "something else"
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.agentHost == "somehost"
    config.agentPort == 123
    config.agentUnixDomainSocket == "somepath"
    config.prioritySamplingEnabled == false
    config.traceResolverEnabled == false
    config.serviceMapping == [a: "1"]
    config.mergedSpanTags == [b: "2", c: "3"]
    config.mergedJmxTags == [b: "2", d: "4", (RUNTIME_ID_TAG): config.getRuntimeId(), (SERVICE): config.serviceName, (LANGUAGE_TAG_KEY): LANGUAGE_TAG_VALUE]
    config.headerTags == [e: "5"]
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 15
    config.propagationStylesToExtract.toList() == [Config.PropagationStyle.B3, Config.PropagationStyle.DATADOG]
    config.propagationStylesToInject.toList() == [Config.PropagationStyle.DATADOG, Config.PropagationStyle.B3]
    config.jmxFetchMetricsConfigs == ["/foo.yaml", "/bar.yaml"]
    config.jmxFetchCheckPeriod == 100
    config.jmxFetchRefreshBeansPeriod == 200
    config.jmxFetchStatsdHost == "statsd host"
    config.jmxFetchStatsdPort == 321
  }

  def "override null properties"() {
    when:
    def config = Config.get(null)

    then:
    config.serviceName == "unnamed-java-app"
    config.writerType == "DDAgentWriter"
  }

  def "override empty properties"() {
    setup:
    Properties properties = new Properties()

    when:
    def config = Config.get(properties)

    then:
    config.serviceName == "unnamed-java-app"
    config.writerType == "DDAgentWriter"
  }

  def "override non empty properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty("foo", "bar")

    when:
    def config = Config.get(properties)

    then:
    config.serviceName == "unnamed-java-app"
    config.writerType == "DDAgentWriter"
  }

  def "verify integration config"() {
    setup:
    environmentVariables.set("DD_INTEGRATION_ORDER_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_TEST_ENV_ENABLED", "true")
    environmentVariables.set("DD_INTEGRATION_DISABLED_ENV_ENABLED", "false")

    System.setProperty("dd.integration.order.enabled", "true")
    System.setProperty("dd.integration.test-prop.enabled", "true")
    System.setProperty("dd.integration.disabled-prop.enabled", "false")

    expect:
    Config.get().isIntegrationEnabled(integrationNames, defaultEnabled) == expected

    where:
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false

    integrationNames = new TreeSet<>(names)
  }

  def "verify integration jmxfetch config"() {
    setup:
    environmentVariables.set("DD_JMXFETCH_ORDER_ENABLED", "false")
    environmentVariables.set("DD_JMXFETCH_TEST_ENV_ENABLED", "true")
    environmentVariables.set("DD_JMXFETCH_DISABLED_ENV_ENABLED", "false")

    System.setProperty("dd.jmxfetch.order.enabled", "true")
    System.setProperty("dd.jmxfetch.test-prop.enabled", "true")
    System.setProperty("dd.jmxfetch.disabled-prop.enabled", "false")

    expect:
    Config.get().isJmxFetchIntegrationEnabled(integrationNames, defaultEnabled) == expected

    where:
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false

    integrationNames = new TreeSet<>(names)
  }

  def "verify integration trace analytics config"() {
    setup:
    environmentVariables.set("DD_ORDER_ANALYTICS_ENABLED", "false")
    environmentVariables.set("DD_TEST_ENV_ANALYTICS_ENABLED", "true")
    environmentVariables.set("DD_DISABLED_ENV_ANALYTICS_ENABLED", "false")

    System.setProperty("dd.order.analytics.enabled", "true")
    System.setProperty("dd.test-prop.analytics.enabled", "true")
    System.setProperty("dd.disabled-prop.analytics.enabled", "false")

    expect:
    Config.get().isTraceAnalyticsIntegrationEnabled(integrationNames, defaultEnabled) == expected

    where:
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false

    integrationNames = new TreeSet<>(names)
  }

  def "test getFloatSettingFromEnvironment(#name)"() {
    setup:
    environmentVariables.set("DD_ENV_ZERO_TEST", "0.0")
    environmentVariables.set("DD_ENV_FLOAT_TEST", "1.0")
    environmentVariables.set("DD_FLOAT_TEST", "0.2")

    System.setProperty("dd.prop.zero.test", "0")
    System.setProperty("dd.prop.float.test", "0.3")
    System.setProperty("dd.float.test", "0.4")
    System.setProperty("dd.garbage.test", "garbage")
    System.setProperty("dd.negative.test", "-1")

    expect:
    Config.getFloatSettingFromEnvironment(name, defaultValue) == (float) expected

    where:
    name              | expected
    "env.zero.test"   | 0.0
    "prop.zero.test"  | 0
    "env.float.test"  | 1.0
    "prop.float.test" | 0.3
    "float.test"      | 0.4
    "negative.test"   | -1.0
    "garbage.test"    | 10.0
    "default.test"    | 10.0

    defaultValue = 10.0
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)
    def props = new Properties()
    props.setProperty(SERVICE_MAPPING, mapString)
    props.setProperty(SPAN_TAGS, mapString)
    props.setProperty(HEADER_TAGS, mapString)

    when:
    def config = new Config()
    def propConfig = Config.get(props)

    then:
    config.serviceMapping == map
    config.spanTags == map
    config.headerTags == map
    propConfig.serviceMapping == map
    propConfig.spanTags == map
    propConfig.headerTags == map

    where:
    mapString                         | map
    "a:1, a:2, a:3"                   | [a: "3"]
    "a:b,c:d,e:"                      | [a: "b", c: "d"]
    // More different string variants:
    "a:"                              | [:]
    "a:a;"                            | [a: "a;"]
    "a:1, a:2, a:3"                   | [a: "3"]
    "a:b,c:d,e:"                      | [a: "b", c: "d"]
    "key 1!:va|ue_1,"                 | ["key 1!": "va|ue_1"]
    " key1 :value1 ,\t key2:  value2" | [key1: "value1", key2: "value2"]
    // Invalid strings:
    ""                                | [:]
    "1"                               | [:]
    "a"                               | [:]
    "a,1"                             | [:]
    "in:val:id"                       | [:]
    "a:b:c:d"                         | [:]
    "a:b,c,d"                         | [:]
    "!a"                              | [:]
  }

  def "verify integer range configs on tracer"() {
    setup:
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, value)
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, value)
    def props = new Properties()
    props.setProperty(HTTP_CLIENT_ERROR_STATUSES, value)
    props.setProperty(HTTP_SERVER_ERROR_STATUSES, value)

    when:
    def config = new Config()
    def propConfig = Config.get(props)

    then:
    if (expected) {
      assert config.httpServerErrorStatuses == expected.toSet()
      assert config.httpClientErrorStatuses == expected.toSet()
      assert propConfig.httpServerErrorStatuses == expected.toSet()
      assert propConfig.httpClientErrorStatuses == expected.toSet()
    } else {
      assert config.httpServerErrorStatuses == Config.DEFAULT_HTTP_SERVER_ERROR_STATUSES
      assert config.httpClientErrorStatuses == Config.DEFAULT_HTTP_CLIENT_ERROR_STATUSES
      assert propConfig.httpServerErrorStatuses == Config.DEFAULT_HTTP_SERVER_ERROR_STATUSES
      assert propConfig.httpClientErrorStatuses == Config.DEFAULT_HTTP_CLIENT_ERROR_STATUSES
    }

    where:
    value               | expected // null means default value
    "1"                 | null
    "a"                 | null
    ""                  | null
    "1000"              | null
    "100-200-300"       | null
    "500"               | [500]
    "100,999"           | [100, 999]
    "999-888"           | 888..999
    "400-403,405-407"   | [400, 401, 402, 403, 405, 406, 407]
    " 400 - 403 , 405 " | [400, 401, 402, 403, 405]
  }

  def "verify null value mapping configs on tracer"() {
    setup:
    environmentVariables.set(DD_SERVICE_MAPPING_ENV, mapString)
    environmentVariables.set(DD_SPAN_TAGS_ENV, mapString)
    environmentVariables.set(DD_HEADER_TAGS_ENV, mapString)

    when:
    def config = new Config()

    then:
    config.serviceMapping == map
    config.spanTags == map
    config.headerTags == map

    where:
    mapString | map
    null      | [:]
    ""        | [:]
  }

  def "verify empty value list configs on tracer"() {
    setup:
    System.setProperty(PREFIX + JMX_FETCH_METRICS_CONFIGS, listString)

    when:
    def config = new Config()

    then:
    config.jmxFetchMetricsConfigs == list

    where:
    listString | list
    ""         | []
  }

  def "verify hostname not added to root span tags by default"() {
    setup:
    Properties properties = new Properties()

    when:
    def config = Config.get(properties)

    then:
    !config.localRootSpanTags.containsKey('_dd.hostname')
  }

  def "verify configuration to add hostname to root span tags"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty(TRACE_REPORT_HOSTNAME, 'true')

    when:
    def config = Config.get(properties)

    then:
    config.localRootSpanTags.get('_dd.hostname') == InetAddress.localHost.hostName
  }

  def "verify fallback to properties file"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")

    when:
    def config = new Config()

    then:
    config.serviceName == "set-in-properties"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }

  def "verify fallback to properties file has lower priority than system property"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")
    System.setProperty(PREFIX + SERVICE_NAME, "set-in-system")

    when:
    def config = new Config()

    then:
    config.serviceName == "set-in-system"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + SERVICE_NAME)
  }

  def "verify fallback to properties file has lower priority than env var"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")
    environmentVariables.set("DD_SERVICE_NAME", "set-in-env")

    when:
    def config = new Config()

    then:
    config.serviceName == "set-in-env"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + SERVICE_NAME)
    environmentVariables.clear("DD_SERVICE_NAME")
  }

  def "verify fallback to properties file that does not exist does not crash app"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/do-not-exist.properties")

    when:
    def config = new Config()

    then:
    config.serviceName == 'unnamed-java-app'

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }
}
