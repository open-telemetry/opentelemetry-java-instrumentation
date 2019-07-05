import datadog.trace.instrumentation.netty40.AttributeKeys
import spock.lang.Specification

class ApacheAtlas1_1_0CompatibilityTest extends Specification {

  def "Netty 4.0 Attributes can be loaded by multiple class loaders in different threads as in Apache Atlas 1.1.0"() {

    expect:
    AttributeKeys.PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY.name().matches(/ClassLoader\..*.datadog\.trace\.instrumentation\.netty40\.parent\.connect\.continuation/)
    AttributeKeys.SERVER_ATTRIBUTE_KEY.name().matches(/ClassLoader\..*\.datadog\.trace\.instrumentation\.netty40\.server\.HttpServerTracingHandler\.span/)
    AttributeKeys.CLIENT_ATTRIBUTE_KEY.name().matches(/ClassLoader\..*\.datadog\.trace\.instrumentation\.netty40\.client\.HttpClientTracingHandler\.span/)
  }
}
