package datadog.trace.tracer

import com.fasterxml.jackson.databind.ObjectMapper
import nl.jqno.equalsverifier.EqualsVerifier
import nl.jqno.equalsverifier.Warning
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.MICROSECONDS
import static java.util.concurrent.TimeUnit.NANOSECONDS

class TimestampTest extends Specification {

  private static final long CLOCK_START_TIME = 100
  private static final long CLOCK_START_NANO_TICKS = 300
  private static final long CLOCK_NANO_TICKS = 500

  private static final long FINISH_NANO_TICKS = 600
  private static final long FINISH_TIME = 700

  def tracer = Mock(Tracer)
  def clock = Mock(Clock) {
    getTracer() >> tracer
  }

  ObjectMapper objectMapper = new ObjectMapper()

  def "test getter"() {
    when:
    def timestamp = new Timestamp(clock)

    then:
    timestamp.getClock() == clock
  }

  def "test getTime"() {
    setup:
    clock.nanoTicks() >> CLOCK_NANO_TICKS
    clock.getStartTimeNano() >> CLOCK_START_TIME
    clock.getStartNanoTicks() >> CLOCK_START_NANO_TICKS
    def timestamp = new Timestamp(clock)

    when:
    def duration = timestamp.getDuration(FINISH_TIME)

    then:
    duration == 400
    0 * tracer._
  }

  def "test timestamp with custom time"() {
    setup:
    clock.nanoTicks() >> CLOCK_NANO_TICKS
    clock.getStartTimeNano() >> CLOCK_START_TIME
    clock.getStartNanoTicks() >> CLOCK_START_NANO_TICKS
    def timestamp = new Timestamp(clock, CLOCK_START_TIME + offset, unit)

    when:
    def time = timestamp.getTime()

    then:
    time == expected

    where:
    offset | unit         | expected
    10     | NANOSECONDS  | 410
    -20    | NANOSECONDS  | 380
    3      | MICROSECONDS | 103300
    -4     | MICROSECONDS | 96300
  }

  def "test getDuration with literal finish time"() {
    setup:
    clock.nanoTicks() >> CLOCK_NANO_TICKS
    clock.getStartTimeNano() >> CLOCK_START_TIME
    clock.getStartNanoTicks() >> CLOCK_START_NANO_TICKS
    def timestamp = new Timestamp(clock)

    when:
    def duration = timestamp.getDuration(FINISH_TIME)

    then:
    duration == 400
    0 * tracer._
  }

  def "test getDuration with timestamp '#name'"() {
    setup:
    clock.nanoTicks() >> startNanoTicks >> finishNanoTicks
    def startTimestamp = new Timestamp(clock)
    def finishTimestamp = new Timestamp(clock)

    when:
    def duration = startTimestamp.getDuration(finishTimestamp)

    then:
    duration == expectedDuration
    0 * tracer._

    where:
    name       | startNanoTicks                          | finishNanoTicks                    | expectedDuration
    'normal'   | CLOCK_START_NANO_TICKS                  | FINISH_NANO_TICKS                  | FINISH_NANO_TICKS - CLOCK_START_NANO_TICKS
    'overflow' | Long.MAX_VALUE - CLOCK_START_NANO_TICKS | Long.MIN_VALUE + FINISH_NANO_TICKS | FINISH_NANO_TICKS + CLOCK_START_NANO_TICKS + 1
  }

  def "test getDuration with current time"() {
    setup:
    clock.createCurrentTimestamp() >> { new Timestamp(clock) }
    clock.nanoTicks() >> CLOCK_START_NANO_TICKS >> FINISH_NANO_TICKS
    def timestamp = new Timestamp(clock)

    when:
    def duration = timestamp.getDuration()

    then:
    duration == FINISH_NANO_TICKS - CLOCK_START_NANO_TICKS
    0 * tracer._
  }

  def "test getDuration with wrong clock"() {
    setup:
    clock.nanoTicks() >> CLOCK_NANO_TICKS
    clock.getStartTimeNano() >> CLOCK_START_TIME
    clock.getStartNanoTicks() >> CLOCK_START_NANO_TICKS
    def timestamp = new Timestamp(clock)
    def otherClock = Mock(Clock) {
      getTracer() >> tracer
      nanoTicks() >> CLOCK_NANO_TICKS + 400
      getStartTimeNano() >> CLOCK_START_TIME + 200
      getStartNanoTicks() >> CLOCK_START_NANO_TICKS + 100
    }
    def finishTimestamp = new Timestamp(otherClock)

    when:
    def duration = timestamp.getDuration(finishTimestamp)

    then:
    duration == 500
    1 * tracer.reportError(_, clock, otherClock)
  }

  def "test equals"() {
    when:
    EqualsVerifier.forClass(Timestamp).suppress(Warning.STRICT_INHERITANCE).verify()

    then:
    noExceptionThrown()
  }

  def "test JSON rendering"() {
    setup:
    clock.nanoTicks() >> CLOCK_NANO_TICKS
    clock.getStartTimeNano() >> CLOCK_START_TIME
    clock.getStartNanoTicks() >> CLOCK_START_NANO_TICKS
    def timestamp = new Timestamp(clock)

    when:
    def string = objectMapper.writeValueAsString(timestamp)


    then:
    string == "300"
  }
}
