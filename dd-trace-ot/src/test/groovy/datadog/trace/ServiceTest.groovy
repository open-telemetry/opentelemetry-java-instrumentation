package datadog.trace

import datadog.opentracing.DDTracer
import datadog.trace.common.Service
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.writer.DDAgentWriter
import spock.lang.Specification
import spock.lang.Timeout

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

@Timeout(1)
class ServiceTest extends Specification {


  def "getter and setter"() {

    setup:
    def service = new Service("api-intake", "kafka", Service.AppType.CUSTOM)

    expect:
    service.getName() == "api-intake"
    service.getAppName() == "kafka"
    service.getAppType() == Service.AppType.CUSTOM
    service.toString() == "Service { name='api-intake', appName='kafka', appType=custom }"

  }

  def "enum"() {

    expect:
    Service.AppType.values().size() == 5
    Service.AppType.DB.toString() == "db"
    Service.AppType.WEB.toString() == "web"
    Service.AppType.CUSTOM.toString() == "custom"
    Service.AppType.WORKER.toString() == "worker"
    Service.AppType.CACHE.toString() == "cache"

  }

  def "add extra info about a specific service"() {

    setup:
    def tracer = new DDTracer()
    def service = new Service("api-intake", "kafka", Service.AppType.CUSTOM)

    when:
    tracer.addServiceInfo(service)

    then:
    tracer.getServiceInfo().size() == 1
    tracer.getServiceInfo().get("api-intake") == service

  }

  def "add a extra info is reported to the writer"() {

    setup:
    def writer = spy(new DDAgentWriter())
    def tracer = new DDTracer(DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME, writer, new AllSampler())


    when:
    tracer.addServiceInfo(new Service("api-intake", "kafka", Service.AppType.CUSTOM))

    then:
    verify(writer, times(1)).writeServices(any(Map))

  }

}
