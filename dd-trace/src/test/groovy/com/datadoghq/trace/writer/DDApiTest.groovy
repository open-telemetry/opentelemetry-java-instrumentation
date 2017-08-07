package com.datadoghq.trace.writer

import com.datadoghq.trace.Service
import com.datadoghq.trace.SpanFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ratpack.http.Headers
import ratpack.http.MediaType
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack
import static ratpack.http.MediaType.APPLICATION_JSON

class DDApiTest extends Specification {
  static mapper = new ObjectMapper()

  def "sending an empty list of traces returns no errors"() {
    setup:
    def agent = ratpack {
      handlers {
        put("v0.3/traces") {
          response.status(200).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendTraces([])

    cleanup:
    agent.close()
  }

  def "non-200 response results in false returned"() {
    setup:
    def agent = ratpack {
      handlers {
        put("v0.3/traces") {
          response.status(404).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    !client.sendTraces([])

    cleanup:
    agent.close()
  }

  def "content is sent as JSON"() {
    setup:
    def requestContentType = new AtomicReference<MediaType>()
    def requestHeaders = new AtomicReference<Headers>()
    def requestBody = new AtomicReference<String>()
    def agent = ratpack {
      handlers {
        put("v0.3/traces") {
          requestContentType.set(request.contentType)
          requestHeaders.set(request.headers)
          request.body.then {
            requestBody.set(it.text)
            response.send()
          }
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendTraces(traces)
    requestContentType.get().type == APPLICATION_JSON
    requestHeaders.get().get("Datadog-Meta-Lang") == "java"
    requestHeaders.get().get("Datadog-Meta-Lang-Version") == System.getProperty("java.version", "unknown")
    requestHeaders.get().get("Datadog-Meta-Tracer-Version") == "unknown"
    convertList(requestBody.get()) == expectedRequestBody

    cleanup:
    agent.close()

    // Populate thread info dynamically as it is different when run via gradle vs idea.
    where:
    traces                        | expectedRequestBody
    []                            | []
    [SpanFactory.newSpanOf(1L)]   | [new TreeMap<>([
      "duration" : 0,
      "error"    : 0,
      "meta"     : ["thread-name": Thread.currentThread().getName(), "thread-id": "${Thread.currentThread().id}"],
      "name"     : "fakeOperation",
      "parent_id": 0,
      "resource" : "fakeResource",
      "service"  : "fakeService",
      "span_id"  : 1,
      "start"    : 1000,
      "trace_id" : 1,
      "type"     : "fakeType"
    ])]
    [SpanFactory.newSpanOf(100L)] | [new TreeMap<>([
      "duration" : 0,
      "error"    : 0,
      "meta"     : ["thread-name": Thread.currentThread().getName(), "thread-id": "${Thread.currentThread().id}"],
      "name"     : "fakeOperation",
      "parent_id": 0,
      "resource" : "fakeResource",
      "service"  : "fakeService",
      "span_id"  : 1,
      "start"    : 100000,
      "trace_id" : 1,
      "type"     : "fakeType"
    ])]
  }

  // Services endpoint
  def "sending an empty map of services returns no errors"() {
    setup:
    def agent = ratpack {
      handlers {
        put("v0.3/services") {
          response.status(200).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendServices()

    cleanup:
    agent.close()
  }

  def "non-200 response results in false returned for services endpoint"() {
    setup:
    def agent = ratpack {
      handlers {
        put("v0.3/services") {
          response.status(404).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    !client.sendServices([:])

    cleanup:
    agent.close()
  }

  def "services content is sent as JSON"() {
    setup:
    def requestContentType = new AtomicReference<MediaType>()
    def requestHeaders = new AtomicReference<Headers>()
    def requestBody = new AtomicReference<String>()
    def agent = ratpack {
      handlers {
        put("v0.3/services") {
          requestContentType.set(request.contentType)
          requestHeaders.set(request.headers)
          request.body.then {
            requestBody.set(it.text)
            response.send()
          }
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendServices(services)
    requestContentType.get().type == APPLICATION_JSON
    requestHeaders.get().get("Datadog-Meta-Lang") == "java"
    requestHeaders.get().get("Datadog-Meta-Lang-Version") == System.getProperty("java.version", "unknown")
    requestHeaders.get().get("Datadog-Meta-Tracer-Version") == "unknown"
    convertMap(requestBody.get()) == expectedRequestBody

    cleanup:
    agent.close()

    // Populate thread info dynamically as it is different when run via gradle vs idea.
    where:
    services                                                                          | expectedRequestBody
    [:]                                                                               | [:]
    ["service-name": new Service("service-name", "app-name", Service.AppType.CUSTOM)] | ["service-name": new TreeMap<>([
      "app"     : "app-name",
      "app_type": "custom"])
    ]
  }

  static List<TreeMap<String, Object>> convertList(String json) {
    return mapper.readValue(json, new TypeReference<List<TreeMap<String, Object>>>() {})
  }

  static TreeMap<String, Object> convertMap(String json) {
    return mapper.readValue(json, new TypeReference<TreeMap<String, Object>>() {})
  }
}
