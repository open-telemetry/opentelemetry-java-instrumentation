package datadog.opentracing

import datadog.trace.api.DDTags
import datadog.trace.common.writer.ListWriter
import io.opentracing.log.Fields
import datadog.trace.util.test.DDSpecification


class DefaultLogHandlerTest extends DDSpecification {
  def writer = new ListWriter()
  def tracer = DDTracer.builder().writer(writer).build()

  def "handles correctly the error passed in the fields"() {
    setup:
    final LogHandler underTest = new DefaultLogHandler()
    final DDSpan span = tracer.buildSpan("op name").withServiceName("foo").start()
    final String errorMessage = "errorMessage"
    final String differentMessage = "differentMessage"
    final Throwable throwable = new Throwable(errorMessage)
    final Map<String, ?> fields = new HashMap<>()
    fields.put(Fields.ERROR_OBJECT, throwable)
    fields.put(Fields.MESSAGE, differentMessage)

    when:
    underTest.log(fields, span)

    then:
    span.getTags().get(DDTags.ERROR_MSG) == throwable.getMessage()
    span.getTags().get(DDTags.ERROR_TYPE) == throwable.getClass().getName()
  }

  def "handles correctly the error passed in the fields when called with timestamp"() {
    setup:
    final LogHandler underTest = new DefaultLogHandler()
    final DDSpan span = tracer.buildSpan("op name").withServiceName("foo").start()
    final String errorMessage = "errorMessage"
    final String differentMessage = "differentMessage"
    final Throwable throwable = new Throwable(errorMessage)
    final Map<String, ?> fields = new HashMap<>()
    fields.put(Fields.ERROR_OBJECT, throwable)
    fields.put(Fields.MESSAGE, differentMessage)

    when:
    underTest.log(System.currentTimeMillis(), fields, span)

    then:
    span.getTags().get(DDTags.ERROR_MSG) == throwable.getMessage()
    span.getTags().get(DDTags.ERROR_TYPE) == throwable.getClass().getName()
  }

  def "handles correctly the message passed in the fields"() {
    setup:
    final LogHandler underTest = new DefaultLogHandler()
    final DDSpan span = tracer.buildSpan("op name").withServiceName("foo").start()
    final String errorMessage = "errorMessage"
    final Map<String, ?> fields = new HashMap<>()
    fields.put(Fields.MESSAGE, errorMessage)

    when:
    underTest.log(fields, span)

    then:
    span.getTags().get(DDTags.ERROR_MSG) == errorMessage
  }

  def "handles correctly the message passed in the fields when called with timestamp"() {
    setup:
    final LogHandler underTest = new DefaultLogHandler()
    final DDSpan span = tracer.buildSpan("op name").withServiceName("foo").start()
    final String errorMessage = "errorMessage"
    final Map<String, ?> fields = new HashMap<>()
    fields.put(Fields.MESSAGE, errorMessage)

    when:
    underTest.log(System.currentTimeMillis(), fields, span)

    then:
    span.getTags().get(DDTags.ERROR_MSG) == errorMessage
  }
}
