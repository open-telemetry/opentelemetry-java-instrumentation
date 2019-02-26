package datadog.trace.tracer

import nl.jqno.equalsverifier.EqualsVerifier
import nl.jqno.equalsverifier.Warning
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class ClockTest extends Specification {

  // Assume it takes less than a minute to run this test
  public static final long MINUTE = TimeUnit.MINUTES.toNanos(1)

  @Shared
  def tracer = Mock(Tracer)

  def "test getters"() {
    setup:
    def currentTimeNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
    def nanoTicks = System.nanoTime()

    when:
    def clock = new Clock(tracer)

    then:
    clock.getTracer() == tracer
    clock.getStartTimeNano() - currentTimeNano <= MINUTE
    clock.getStartNanoTicks() - nanoTicks <= MINUTE
    clock.epochTimeNano() - currentTimeNano <= MINUTE
    TimeUnit.MICROSECONDS.toNanos(clock.epochTimeMicro()) - currentTimeNano <= MINUTE
    clock.nanoTicks() - nanoTicks <= MINUTE
  }

  def "test timestamp creation"() {
    setup:
    def clock = new Clock(tracer)

    when:
    def timestamp = clock.createCurrentTimestamp()

    then:
    timestamp.getClock() == clock
  }

  def "test timestamp creation with custom time"() {
    setup:
    def clock = new Clock(tracer)

    when:
    def timestamp = clock.createTimestampForTime(1, TimeUnit.SECONDS)

    then:
    timestamp.getClock() == clock
  }

  def "test equals"() {
    when:
    EqualsVerifier.forClass(Clock).suppress(Warning.STRICT_INHERITANCE).verify()

    then:
    noExceptionThrown()
  }
}
