package datadog.trace

import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.DatadogHttpCodec
import datadog.trace.api.Config
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static datadog.trace.api.Config.PREFIX
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
    tracer.spanContextDecorators.size() == 5

    tracer.injector instanceof DatadogHttpCodec.Injector
    tracer.extractor instanceof DatadogHttpCodec.Extractor
  }

  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify writer constructor"() {
    setup:
    def writer = new ListWriter()

    when:
    def tracer = new DDTracer(writer)

    then:
    tracer.writer == writer
  }
}
