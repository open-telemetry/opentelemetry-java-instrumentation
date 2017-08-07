package com.datadoghq.trace

import com.datadoghq.trace.sampling.AllSampler
import com.datadoghq.trace.writer.DDAgentWriter
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

class ServiceTest extends Specification {


  def "getter and setter"() {

    setup:
    def service = new Service("service-name", "app-name", Service.AppType.CUSTOM)

    expect:
    service.getName() == "service-name"
    service.getAppName() == "app-name"
    service.getAppType() == Service.AppType.CUSTOM

  }

  def "enum"() {

    expect:
    Service.AppType.values().size() == 3
    Service.AppType.DB.toString() == "db"
    Service.AppType.WEB.toString() == "web"
    Service.AppType.CUSTOM.toString() == "custom"

  }

  def "add extra info about a specific service"() {

    setup:
    def tracer = new DDTracer()
    def service = new Service("service-name", "app-name", Service.AppType.CUSTOM)

    when:
    tracer.addServiceInfo(service)

    then:
    tracer.getServiceInfo().size() == 1
    tracer.getServiceInfo().get("service-name") == service

  }

  def "add a extra info is reported to the writer"() {

    setup:
    def writer = spy(new DDAgentWriter())
    def tracer = new DDTracer(writer, new AllSampler())


    when:
    tracer.addServiceInfo(new Service("service-name", "app-name", Service.AppType.CUSTOM))

    then:
    verify(writer, times(1)).writeServices(any(Map))

  }

}
