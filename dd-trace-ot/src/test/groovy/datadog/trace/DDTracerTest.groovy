package datadog.trace


import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.DatadogHttpCodec
import datadog.opentracing.propagation.HttpCodec
import datadog.trace.api.Config
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.sampling.PrioritySampler
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.sampling.Sampler
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.common.writer.ddagent.Monitor
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInject
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Timeout

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.Config.HEADER_TAGS
import static datadog.trace.api.Config.HEALTH_METRICS_ENABLED
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.PRIORITY_SAMPLING
import static datadog.trace.api.Config.SERVICE_MAPPING
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT

@Timeout(10)
class DDTracerTest extends DDSpecification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def "verify defaults on tracer"() {
    when:
    def tracer = DDTracer.builder().build()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof RateByServiceSampler
    tracer.writer instanceof DDAgentWriter
    tracer.writer.monitor instanceof Monitor.Noop

    !tracer.spanContextDecorators.isEmpty()

    tracer.injector instanceof HttpCodec.CompoundInjector
    tracer.extractor instanceof HttpCodec.CompoundExtractor
  }

  def "verify enabling health monitor"() {
    setup:
    System.setProperty(PREFIX + HEALTH_METRICS_ENABLED, "true")

    when:
    def tracer = DDTracer.builder().config(new Config()).build()

    then:
    tracer.writer.monitor instanceof Monitor.StatsD
    tracer.writer.monitor.hostInfo == "localhost:8125"
  }


  def "verify overriding sampler"() {
    setup:
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "false")
    when:
    def tracer = DDTracer.builder().config(new Config()).build()
    then:
    tracer.sampler instanceof AllSampler
  }

  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = DDTracer.builder().config(new Config()).build()

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)

    when:
    def tracer = DDTracer.builder().config(new Config()).build()
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

  def "verify overriding host"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = DDTracer.builder().config(new Config()).build()
    then:
    tracer.writer instanceof DDAgentWriter
    ((DDAgentWriter) tracer.writer).api.sendTraces([])
    ((DDAgentWriter) tracer.writer).api.tracesUrl.host() == value
    ((DDAgentWriter) tracer.writer).api.tracesUrl.port() == 8126

    where:
    key          | value
    "agent.host" | "somethingelse"
  }

  def "verify overriding port"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = DDTracer.builder().config(new Config()).build()

    then:
    tracer.writer instanceof DDAgentWriter
    ((DDAgentWriter) tracer.writer).api.sendTraces([])
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
    def tracer = DDTracer.builder().config(new Config()).build()

    then:
    tracer.writer instanceof LoggingWriter
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
    final DDTracer tracer = DDTracer.builder().build()

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
    def tracer = DDTracer.builder().localRootSpanTags(['only_root': 'value']).build()
    def root = tracer.buildSpan('my_root').start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()

    expect:
    root.context().tags.containsKey('only_root')
    !child.context().tags.containsKey('only_root')

    cleanup:
    child.finish()
    root.finish()
  }

  def "priority sampling when span finishes"() {
    given:
    Properties properties = new Properties()
    properties.setProperty("writer.type", "LoggingWriter")
    def tracer = DDTracer.builder().withProperties(properties).build()

    when:
    def span = tracer.buildSpan("operation").start()
    span.finish()

    then:
    span.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
  }

  def "priority sampling set when child span complete"() {
    given:
    Properties properties = new Properties()
    properties.setProperty("writer.type", "LoggingWriter")
    def tracer = DDTracer.builder().withProperties(properties).build()

    when:
    def root = tracer.buildSpan("operation").start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()
    root.finish()

    then:
    root.getSamplingPriority() == null

    when:
    child.finish()

    then:
    root.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    child.getSamplingPriority() == root.getSamplingPriority()
  }

  def "span priority set when injecting"() {
    given:
    Properties properties = new Properties()
    properties.setProperty("writer.type", "LoggingWriter")
    def tracer = DDTracer.builder().withProperties(properties).build()
    def injector = Mock(TextMapInject)

    when:
    def root = tracer.buildSpan("operation").start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()
    tracer.inject(child.context(), TEXT_MAP_INJECT, injector)

    then:
    root.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    child.getSamplingPriority() == root.getSamplingPriority()
    1 * injector.put(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, String.valueOf(PrioritySampling.SAMPLER_KEEP))

    cleanup:
    child.finish()
    root.finish()
  }

  def "span priority only set after first injection"() {
    given:
    def sampler = new ControllableSampler()
    def tracer = DDTracer.builder().writer(new LoggingWriter()).sampler(sampler).build()
    def injector = Mock(TextMapInject)

    when:
    def root = tracer.buildSpan("operation").start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()
    tracer.inject(child.context(), TEXT_MAP_INJECT, injector)

    then:
    root.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    child.getSamplingPriority() == root.getSamplingPriority()
    1 * injector.put(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, String.valueOf(PrioritySampling.SAMPLER_KEEP))

    when:
    sampler.nextSamplingPriority = PrioritySampling.SAMPLER_DROP
    def child2 = tracer.buildSpan('my_child2').asChildOf(root).start()
    tracer.inject(child2.context(), TEXT_MAP_INJECT, injector)

    then:
    root.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    child.getSamplingPriority() == root.getSamplingPriority()
    child2.getSamplingPriority() == root.getSamplingPriority()
    1 * injector.put(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, String.valueOf(PrioritySampling.SAMPLER_KEEP))

    cleanup:
    child.finish()
    child2.finish()
    root.finish()
  }

  def "injection doesn't override set priority"() {
    given:
    def sampler = new ControllableSampler()
    def tracer = DDTracer.builder().writer(new LoggingWriter()).sampler(sampler).build()
    def injector = Mock(TextMapInject)

    when:
    def root = tracer.buildSpan("operation").start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()
    child.setSamplingPriority(PrioritySampling.USER_DROP)
    tracer.inject(child.context(), TEXT_MAP_INJECT, injector)

    then:
    root.getSamplingPriority() == PrioritySampling.USER_DROP
    child.getSamplingPriority() == root.getSamplingPriority()
    1 * injector.put(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, String.valueOf(PrioritySampling.USER_DROP))

    cleanup:
    child.finish()
    root.finish()
  }
}

class ControllableSampler implements Sampler, PrioritySampler {
  protected int nextSamplingPriority = PrioritySampling.SAMPLER_KEEP

  @Override
  void setSamplingPriority(DDSpan span) {
    span.setSamplingPriority(nextSamplingPriority)
  }

  @Override
  boolean sample(DDSpan span) {
    return true
  }
}
