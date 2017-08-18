package com.datadoghq.trace

import com.datadoghq.trace.sampling.AllSampler
import com.datadoghq.trace.writer.DDAgentWriter
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

class ServiceTest extends Specification {


  def "getter and setter"() {

    setup:
    def service = new Service("api-intake", "kafka", Service.AppType.CUSTOM)

    expect:
    service.getName() == "api-intake"
    service.getAppName() == "kafka"
    service.getAppType() == Service.AppType.CUSTOM

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
    def tracer = new DDTracer(writer, new AllSampler())


    when:
    tracer.addServiceInfo(new Service("api-intake", "kafka", Service.AppType.CUSTOM))

    then:
    verify(writer, times(1)).writeServices(any(Map))

  }

}
