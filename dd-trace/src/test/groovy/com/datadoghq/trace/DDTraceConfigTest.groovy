package com.datadoghq.trace

import com.datadoghq.trace.sampling.AllSampler
import com.datadoghq.trace.sampling.RateSampler
import com.datadoghq.trace.writer.DDAgentWriter
import com.datadoghq.trace.writer.ListWriter
import com.datadoghq.trace.writer.LoggingWriter
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import static com.datadoghq.trace.DDTraceConfig.*

class DDTraceConfigTest extends Specification {
  static originalEnvMap
  static overrideEnvMap = new HashMap<String, String>()

  def setupSpec() {
    def envMapField = ProcessEnvironment.getDeclaredField("theUnmodifiableEnvironment")
    envMapField.setAccessible(true)

    Field modifiersField = Field.getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(envMapField, envMapField.getModifiers() & ~Modifier.FINAL)

    originalEnvMap = envMapField.get(null)
    overrideEnvMap.putAll(originalEnvMap)
    envMapField.set(null, overrideEnvMap)
  }

  def cleanupSpec() {
    def envMapField = ProcessEnvironment.getDeclaredField("theUnmodifiableEnvironment")
    envMapField.setAccessible(true)

    Field modifiersField = Field.getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(envMapField, envMapField.getModifiers() & ~Modifier.FINAL)

    originalEnvMap = envMapField.get(null)
    envMapField.set(null, originalEnvMap)
  }

  def setup() {
    overrideEnvMap.clear()
    overrideEnvMap.putAll(originalEnvMap)

    System.clearProperty(PREFIX + SERVICE_NAME)
    System.clearProperty(PREFIX + WRITER_TYPE)
    System.clearProperty(PREFIX + AGENT_HOST)
    System.clearProperty(PREFIX + AGENT_PORT)
    System.clearProperty(PREFIX + SAMPLER_TYPE)
    System.clearProperty(PREFIX + SAMPLER_RATE)
  }

  def "verify env override"() {
    setup:
    overrideEnvMap.put("SOME_RANDOM_ENTRY", "asdf")

    expect:
    System.getenv("SOME_RANDOM_ENTRY") == "asdf"
  }

  def "verify defaults"() {
    when:
    def config = new DDTraceConfig()

    then:
    config.getProperty(SERVICE_NAME) == "unnamed-java-app"
    config.getProperty(WRITER_TYPE) == null
    config.getProperty(AGENT_HOST) == null
    config.getProperty(AGENT_PORT) == null
    config.getProperty(SAMPLER_TYPE) == null
    config.getProperty(SAMPLER_RATE) == null

    when:
    config = new DDTraceConfig("A different service name")

    then:
    config.getProperty(SERVICE_NAME) == "A different service name"
    config.getProperty(WRITER_TYPE) == null
    config.getProperty(AGENT_HOST) == null
    config.getProperty(AGENT_PORT) == null
    config.getProperty(SAMPLER_TYPE) == null
    config.getProperty(SAMPLER_RATE) == null
  }

  def "specify overrides via system properties"() {
    when:
    System.setProperty(PREFIX + SERVICE_NAME, "something else")
    System.setProperty(PREFIX + WRITER_TYPE, LoggingWriter.simpleName)
    System.setProperty(PREFIX + SAMPLER_TYPE, RateSampler.simpleName)
    System.setProperty(PREFIX + SAMPLER_RATE, ".5")
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "something else"
    tracer.writer instanceof LoggingWriter
    tracer.sampler.toString() == "RateSampler { sampleRate=0.5 }"
  }

  def "specify overrides via env vars"() {
    when:
    overrideEnvMap.put(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    overrideEnvMap.put(propToEnvName(PREFIX + WRITER_TYPE), LoggingWriter.simpleName)
    overrideEnvMap.put(propToEnvName(PREFIX + SAMPLER_TYPE), AllSampler.simpleName)
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "still something else"
    tracer.writer instanceof LoggingWriter
    tracer.sampler instanceof AllSampler
  }

  def "sys props override env vars"() {
    when:
    overrideEnvMap.put(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    overrideEnvMap.put(propToEnvName(PREFIX + WRITER_TYPE), ListWriter.simpleName)
    overrideEnvMap.put(propToEnvName(PREFIX + SAMPLER_TYPE), AllSampler.simpleName)

    System.setProperty(PREFIX + SERVICE_NAME, "what we actually want")
    System.setProperty(PREFIX + WRITER_TYPE, DDAgentWriter.simpleName)
    System.setProperty(PREFIX + AGENT_HOST, "somewhere")
    System.setProperty(PREFIX + AGENT_PORT, "9999")
    System.setProperty(PREFIX + SAMPLER_TYPE, RateSampler.simpleName)
    System.setProperty(PREFIX + SAMPLER_RATE, ".9")

    def tracer = new DDTracer()

    then:
    tracer.serviceName == "what we actually want"
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://somewhere:9999/v0.3/traces } }"
    tracer.sampler.toString() == "RateSampler { sampleRate=0.9 }"
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof AllSampler
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"

    tracer.spanContextDecorators.size() == 2
  }
}
