package io.opentelemetry.instrumentation.auto.servlet.v3_0

import javax.servlet.ServletOutputStream
import spock.lang.Specification

class CountingServletOutputStreamClassTest extends Specification {
  def delegateStream = Mock(ServletOutputStream)

  def "should delegate calls to the actual stream"() {
    given:
    def countingStream = CountingServletOutputStreamClass.wrap(delegateStream)

    when:
    countingStream.write(42)
    countingStream.write([12, 42] as byte[])
    countingStream.write([12, 42] as byte[], 0, 2)
    countingStream.flush()
    countingStream.close()

    then:
    1 * delegateStream.write(42)
    1 * delegateStream.write([12, 42] as byte[])
    1 * delegateStream.write([12, 42] as byte[], 0, 2)
    1 * delegateStream.flush()
    1 * delegateStream.close()
  }

  def "should get empty counter"() {
    given:
    def countingStream = CountingServletOutputStreamClass.wrap(delegateStream)

    expect:
    CountingServletOutputStreamClass.getCounter(countingStream) == 0
  }

  def "should increment counter"() {
    given:
    def countingStream = CountingServletOutputStreamClass.wrap(delegateStream)

    when:
    countingStream.write(10)
    countingStream.write([1, 2, 3] as byte[])
    countingStream.write([1, 2, 3, 4, 5] as byte[], 1, 3)

    then:
    CountingServletOutputStreamClass.getCounter(countingStream) == 7
  }
}
