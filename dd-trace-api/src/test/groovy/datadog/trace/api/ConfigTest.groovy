package datadog.trace.api

import datadog.trace.util.test.DDSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static datadog.trace.api.Config.CONFIGURATION_FILE
import static datadog.trace.api.Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE
import static datadog.trace.api.Config.HTTP_CLIENT_ERROR_STATUSES
import static datadog.trace.api.Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN
import static datadog.trace.api.Config.HTTP_SERVER_ERROR_STATUSES
import static datadog.trace.api.Config.PARTIAL_FLUSH_MIN_SPANS
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.RUNTIME_CONTEXT_FIELD_INJECTION
import static datadog.trace.api.Config.TRACE_ENABLED
import static datadog.trace.api.Config.WRITER_TYPE

class ConfigTest extends DDSpecification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  private static final DD_TRACE_ENABLED_ENV = "DD_TRACE_ENABLED"
  private static final DD_WRITER_TYPE_ENV = "DD_WRITER_TYPE"

  def "verify defaults"() {
    when:
    Config config = provider()

    then:
    config.traceEnabled == true
    config.writerType == "LoggingWriter"
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..599).toSet()
    config.httpClientSplitByDomain == false
    config.dbClientSplitByInstance == false
    config.partialFlushMinSpans == 1000
    config.runtimeContextFieldInjection == true
    config.toString().contains("LoggingWriter")

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
    prop.setProperty(TRACE_ENABLED, "false")
    prop.setProperty(WRITER_TYPE, "LoggingWriter")
    prop.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    prop.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    prop.setProperty(HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    prop.setProperty(DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    prop.setProperty(PARTIAL_FLUSH_MIN_SPANS, "15")
    prop.setProperty(RUNTIME_CONTEXT_FIELD_INJECTION, "false")

    when:
    Config config = Config.get(prop)

    then:
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 15
    config.runtimeContextFieldInjection == false
  }

  def "specify overrides via system properties"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, "false")
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "111")
    System.setProperty(PREFIX + HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    System.setProperty(PREFIX + DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    System.setProperty(PREFIX + PARTIAL_FLUSH_MIN_SPANS, "25")
    System.setProperty(PREFIX + RUNTIME_CONTEXT_FIELD_INJECTION, "false")

    when:
    Config config = new Config()

    then:
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 25
    config.runtimeContextFieldInjection == false
  }

  def "specify overrides via env vars"() {
    setup:
    environmentVariables.set(DD_TRACE_ENABLED_ENV, "false")
    environmentVariables.set(DD_WRITER_TYPE_ENV, "LoggingWriter")

    when:
    def config = new Config()

    then:
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
  }

  def "sys props override env vars"() {
    setup:
    environmentVariables.set(DD_WRITER_TYPE_ENV, "LoggingWriter")

    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def config = new Config()

    then:
    config.writerType == "LoggingWriter"
  }

  def "default when configured incorrectly"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, " ")
    System.setProperty(PREFIX + WRITER_TYPE, " ")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "1111")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "1:1")
    System.setProperty(PREFIX + HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "invalid")
    System.setProperty(PREFIX + DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "invalid")

    when:
    def config = new Config()

    then:
    config.traceEnabled == true
    config.writerType == " "
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..599).toSet()
    config.httpClientSplitByDomain == false
    config.dbClientSplitByInstance == false
  }

  def "sys props override properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty(TRACE_ENABLED, "false")
    properties.setProperty(WRITER_TYPE, "LoggingWriter")
    properties.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    properties.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    properties.setProperty(HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true")
    properties.setProperty(DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
    properties.setProperty(PARTIAL_FLUSH_MIN_SPANS, "15")

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == false
    config.writerType == "LoggingWriter"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.httpClientSplitByDomain == true
    config.dbClientSplitByInstance == true
    config.partialFlushMinSpans == 15
  }

  def "override null properties"() {
    when:
    def config = Config.get(null)

    then:
    config.writerType == "LoggingWriter"
  }

  def "override empty properties"() {
    setup:
    Properties properties = new Properties()

    when:
    def config = Config.get(properties)

    then:
    config.writerType == "LoggingWriter"
  }

  def "override non empty properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty("foo", "bar")

    when:
    def config = Config.get(properties)

    then:
    config.writerType == "LoggingWriter"
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

  def "verify fallback to properties file"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")

    when:
    def config = new Config()

    then:
    config.writerType == "set-in-properties"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }

  def "verify fallback to properties file has lower priority than system property"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")
    System.setProperty(PREFIX + WRITER_TYPE, "set-in-system")

    when:
    def config = new Config()

    then:
    config.writerType == "set-in-system"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + WRITER_TYPE)
  }

  def "verify fallback to properties file has lower priority than env var"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/dd-java-tracer.properties")
    environmentVariables.set("DD_WRITER_TYPE", "set-in-env")

    when:
    def config = new Config()

    then:
    config.writerType == "set-in-env"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + WRITER_TYPE)
    environmentVariables.clear("DD_WRITER_TYPE")
  }

  def "verify fallback to properties file that does not exist does not crash app"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/do-not-exist.properties")

    when:
    def config = new Config()

    then:
    config.writerType == 'LoggingWriter'

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }
}
