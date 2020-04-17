package datadog.opentracing.jfr.openjdk

import datadog.opentracing.jfr.DDNoopScopeEvent
import spock.lang.Requires
import spock.lang.Specification

@Requires({ jvm.java11Compatible })
class ScopeEventFactoryTest extends Specification {

  def factory = new ScopeEventFactory()

  def "Returns noop event if profiling is not running"() {
    when:
    def event = factory.create(null)

    then:
    event == DDNoopScopeEvent.INSTANCE
  }

  def "Returns real event if profiling is running"() {
    setup:
    def recording = JfrHelper.startRecording()

    when:
    def event = factory.create(null)
    JfrHelper.stopRecording(recording)

    then:
    event instanceof ScopeEvent
  }
}
