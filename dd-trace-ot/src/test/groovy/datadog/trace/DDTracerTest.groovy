package datadog.trace

import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.HttpCodec
import datadog.trace.api.Config
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.Config.HEADER_TAGS
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.PRIORITY_SAMPLING
import static datadog.trace.api.Config.SERVICE_MAPPING
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE

class DDTracerTest extends Specification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def setupSpec() {
    // assert that a trace agent isn't running locally as that messes up the test.
    try {
      (new Socket("localhost", 8126)).close()
      throw new IllegalStateException("An agent is already running locally on port 8126. Please stop it if you want to run tests locally.")
    } catch (final ConnectException ioe) {
      // trace agent is not running locally.
    }
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof RateByServiceSampler
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:8126/v0.3/traces } }"

    tracer.spanContextDecorators.size() == 15

    tracer.injector instanceof HttpCodec.CompoundInjector
    tracer.extractor instanceof HttpCodec.CompoundExtractor
  }


  def "verify overriding sampler"() {
    setup:
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "false")
    when:
    def tracer = new DDTracer(new Config())
    then:
    tracer.sampler instanceof AllSampler
  }

  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)

    when:
    def config = new Config()
    def tracer = new DDTracer(config)
    // Datadog extractor gets placed first
    def taggedHeaders = tracer.extractor.extractors[0].taggedHeaders

    then:
    tracer.defaultSpanTags == map
    tracer.serviceNameMappings == map
    taggedHeaders == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d,e:"    | [a: "b", c: "d"]
  }

  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer."$source".toString() == expected

    where:

    source   | key                | value           | expected
    "writer" | "default"          | "default"       | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:8126/v0.3/traces } }"
    "writer" | "writer.type"      | "LoggingWriter" | "LoggingWriter { }"
    "writer" | "agent.host"       | "somethingelse" | "DDAgentWriter { api=DDApi { tracesUrl=http://somethingelse:8126/v0.3/traces } }"
    "writer" | "agent.port"       | "777"           | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:777/v0.3/traces } }"
    "writer" | "trace.agent.port" | "9999"          | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:9999/v0.3/traces } }"
  }

  def "verify sampler/writer constructor"() {
    setup:
    def writer = new ListWriter()
    def sampler = new RateByServiceSampler()

    when:
    def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer, sampler)

    then:
    tracer.serviceName == DEFAULT_SERVICE_NAME
    tracer.sampler == sampler
    tracer.writer == writer
    tracer.localRootSpanTags[Config.RUNTIME_ID_TAG].size() > 0 // not null or empty
    tracer.localRootSpanTags[Config.LANGUAGE_TAG_KEY] == Config.LANGUAGE_TAG_VALUE
  }

  def "Shares TraceCount with DDApi with #key = #value"() {
    setup:
    System.setProperty(PREFIX + key, value)
    final DDTracer tracer = new DDTracer(new Config())

    expect:
    tracer.writer instanceof DDAgentWriter
    tracer.writer.traceCount.is(((DDAgentWriter) tracer.writer).traceCount)

    where:
    key               | value
    PRIORITY_SAMPLING | "true"
    PRIORITY_SAMPLING | "false"
  }

  def "root tags are applied only to root spans"() {
    setup:
    def tracer = new DDTracer('my_service', new ListWriter(), new AllSampler(), '', ['only_root': 'value'], [:], [:], [:])
    def root = tracer.buildSpan('my_root').start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()

    expect:
    root.context().tags.containsKey('only_root')
    !child.context().tags.containsKey('only_root')

    cleanup:
    child.finish()
    root.finish()
  }
}
