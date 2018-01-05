package datadog.opentracing

import spock.lang.Specification

class DDSpanTest extends Specification {

  def "getters and setters"() {
    setup:
    final DDSpanContext context =
        new DDSpanContext(
            1L,
            1L,
            0L,
            "fakeService",
            "fakeOperation",
            "fakeResource",
            Collections.<String, String>emptyMap(),
            false,
            "fakeType",
            null,
            null,
            null)

    final DDSpan span = new DDSpan(1L, context)

    when:
    span.setServiceName("service")
    then:
    span.getServiceName() == "service"

    when:
    span.setOperationName("operation")
    then:
    span.getOperationName() == "operation"

    when:
    span.setResourceName("resource")
    then:
    span.getResourceName() == "resource"

    when:
    span.setSpanType("type")
    then:
    span.getType() == "type"
  }

  def "resource name equals operation name if null"() {
    setup:
    final String opName = "operationName"
    DDSpan span

    when:
    span = new DDTracer().buildSpan(opName).startManual()
    then:
    span.getResourceName() == opName
    span.getServiceName() == DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME

    when:
    final String resourceName = "fake"
    final String serviceName = "myService"
    span = new DDTracer()
            .buildSpan(opName)
            .withResourceName(resourceName)
            .withServiceName(serviceName)
            .startManual()
    then:
    span.getResourceName() == resourceName
    span.getServiceName() == serviceName
  }
}
