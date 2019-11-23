package datadog.trace

import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.DatadogHttpCodec
import datadog.trace.api.Config
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.Config.HEALTH_METRICS_ENABLED
import static datadog.trace.api.Config.HEALTH_METRICS_STATSD_PORT
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE

class DDTracerTest extends DDSpecification {

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
    ((DDAgentWriter) tracer.writer).api.tracesUrl.port() == 8126
    ((DDAgentWriter) tracer.writer).api.tracesUrl.encodedPath() == "/v0.3/traces" ||
      ((DDAgentWriter) tracer.writer).api.tracesUrl.encodedPath() == "/v0.4/traces"
    tracer.writer.monitor instanceof DDAgentWriter.NoopMonitor

    tracer.spanContextDecorators.size() == 12

    tracer.injector instanceof DatadogHttpCodec.Injector
    tracer.extractor instanceof DatadogHttpCodec.Extractor
  }

  def "verify enabling health monitor"() {
    setup:
    System.setProperty(PREFIX + HEALTH_METRICS_ENABLED, "true")
    System.setProperty(PREFIX + HEALTH_METRICS_STATSD_PORT, "8125")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer.monitor instanceof DDAgentWriter.StatsDMonitor
    tracer.writer.monitor.hostInfo == "localhost:8125"
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
    System.setProperty(PREFIX + SPAN_TAGS, mapString)

    when:
    def config = new Config()
    def tracer = new DDTracer(config)

    then:
    tracer.defaultSpanTags == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d,e:"    | [a: "b", c: "d"]
  }

  def "verify overriding host"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof DDAgentWriter
    ((DDAgentWriter) tracer.writer).api.tracesUrl.host() == value
    ((DDAgentWriter) tracer.writer).api.tracesUrl.port() == 8126

    where:
    key          | value
    "agent.host" | "somethingelse"
  }

  def "verify overriding port"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof DDAgentWriter
    ((DDAgentWriter) tracer.writer).api.tracesUrl.host() == "localhost"
    ((DDAgentWriter) tracer.writer).api.tracesUrl.port() == Integer.valueOf(value)

    where:
    key                | value
    "agent.port"       | "777"
    "trace.agent.port" | "9999"
  }

  def "Writer is instance of LoggingWriter when property set"() {
    when:
    System.setProperty(PREFIX + "writer.type", "LoggingWriter")
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify writer constructor"() {
    setup:
    def writer = new ListWriter()

    when:
    def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer)

    then:
    tracer.serviceName == DEFAULT_SERVICE_NAME
    tracer.writer == writer
    tracer.localRootSpanTags[Config.RUNTIME_ID_TAG].size() > 0 // not null or empty
    tracer.localRootSpanTags[Config.LANGUAGE_TAG_KEY] == Config.LANGUAGE_TAG_VALUE
  }

  def "root tags are applied only to root spans"() {
    setup:
    def tracer = new DDTracer('my_service', new ListWriter(), '', ['only_root': 'value'], [:])
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
