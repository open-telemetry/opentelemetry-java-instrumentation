package datadog.trace

import datadog.opentracing.DDTracer
import datadog.trace.common.DDTraceConfig
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

import static datadog.trace.common.DDTraceConfig.AGENT_HOST
import static datadog.trace.common.DDTraceConfig.AGENT_PORT
import static datadog.trace.common.DDTraceConfig.HEADER_TAGS
import static datadog.trace.common.DDTraceConfig.PREFIX
import static datadog.trace.common.DDTraceConfig.SERVICE_MAPPING
import static datadog.trace.common.DDTraceConfig.SERVICE_NAME
import static datadog.trace.common.DDTraceConfig.SPAN_TAGS
import static datadog.trace.common.DDTraceConfig.WRITER_TYPE
import static datadog.trace.common.util.Config.parseMap
import static datadog.trace.common.util.Config.propToEnvName

class DDTraceConfigTest extends Specification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def "verify env override"() {
    setup:
    environmentVariables.set("SOME_RANDOM_ENTRY", "asdf")

    expect:
    System.getenv("SOME_RANDOM_ENTRY") == "asdf"
  }

  def "verify defaults"() {
    when:
    def config = new DDTraceConfig()

    then:
    config.getProperty(SERVICE_NAME) == "unnamed-java-app"
    config.getProperty(SERVICE_MAPPING) == null
    config.getProperty(WRITER_TYPE) == "DDAgentWriter"
    config.getProperty(AGENT_HOST) == "localhost"
    config.getProperty(AGENT_PORT) == "8126"
    config.getProperty(SPAN_TAGS) == null

    when:
    config = new DDTraceConfig("A different service name")

    then:
    config.getProperty(SERVICE_NAME) == "A different service name"
    config.getProperty(SERVICE_MAPPING) == null
    config.getProperty(WRITER_TYPE) == "DDAgentWriter"
    config.getProperty(AGENT_HOST) == "localhost"
    config.getProperty(AGENT_PORT) == "8126"
    config.getProperty(SPAN_TAGS) == null
  }

  def "specify overrides via system properties"() {
    when:
    System.setProperty(PREFIX + SERVICE_NAME, "something else")
    System.setProperty(PREFIX + WRITER_TYPE, LoggingWriter.simpleName)
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "something else"
    tracer.writer instanceof LoggingWriter
  }

  def "specify overrides via env vars"() {
    when:
    environmentVariables.set(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    environmentVariables.set(propToEnvName(PREFIX + WRITER_TYPE), LoggingWriter.simpleName)
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "still something else"
    tracer.writer instanceof LoggingWriter
  }

  def "sys props override env vars"() {
    when:
    environmentVariables.set(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    environmentVariables.set(propToEnvName(PREFIX + WRITER_TYPE), ListWriter.simpleName)

    System.setProperty(PREFIX + SERVICE_NAME, "what we actually want")
    System.setProperty(PREFIX + WRITER_TYPE, DDAgentWriter.simpleName)
    System.setProperty(PREFIX + AGENT_HOST, "somewhere")
    System.setProperty(PREFIX + AGENT_PORT, "9999")

    def tracer = new DDTracer()

    then:
    tracer.serviceName == "what we actually want"
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://somewhere:9999/v0.3/traces } }"
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof AllSampler
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"

    tracer.spanContextDecorators.size() == 10
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)

    when:
    def tracer = new DDTracer()
    def taggedHeaders = tracer.registry.codecs.values().first().taggedHeaders

    then:
    tracer.spanTags == map
    tracer.serviceNameMappings == map
    taggedHeaders == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d"       | [a: "b", c: "d"]
  }

  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer()

    then:
    tracer."$source".toString() == expected

    where:

    source   | key           | value           | expected
    "writer" | "default"     | "default"       | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"
    "writer" | "writer.type" | "LoggingWriter" | "LoggingWriter { }"
    "writer" | "agent.host"  | "somethingelse" | "DDAgentWriter { api=DDApi { tracesEndpoint=http://somethingelse:8126/v0.3/traces } }"
    "writer" | "agent.port"  | "9999"          | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:9999/v0.3/traces } }"
  }

  def "parsing valid string returns a map"() {
    expect:
    parseMap(str) == map

    where:
    str                               | map
    "a:a;"                            | [a: "a;"]
    "a:1, a:2, a:3"                   | [a: "3"]
    "a:b,c:d"                         | [a: "b", c: "d"]
    "key 1!:va|ue_1,"                 | ["key 1!": "va|ue_1"]
    " key1 :value1 ,\t key2:  value2" | [key1: "value1", key2: "value2"]
  }

  def "parsing an invalid string returns an empty map"() {
    expect:
    parseMap(str) == [:]

    where:
    str         | _
    null        | _
    ""          | _
    "1"         | _
    "a"         | _
    "a:"        | _
    "a,1"       | _
    "in:val:id" | _
    "a:b:c:d"   | _
    "a:b,c,d"   | _
    "!a"        | _
  }
}
