package datadog.trace.api.writer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import datadog.trace.SpanFactory
import datadog.trace.common.Service
import datadog.trace.common.writer.DDApi
import datadog.trace.common.writer.DDApi.ResponseListener
import org.msgpack.jackson.dataformat.MessagePackFactory
import ratpack.http.Headers
import ratpack.http.MediaType
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class DDApiTest extends Specification {
  static mapper = new ObjectMapper(new MessagePackFactory())

  def "sending an empty list of traces returns no errors"() {
    setup:
    def agent = ratpack {
      handlers {
        put("v0.4/traces") {
          response.status(200).send()
        }
        put("v0.4/services") {
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
        put("v0.4/traces") {
          response.status(404).send()
        }
        put("v0.4/services") {
          response.status(200).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    !client.sendTraces([])

    cleanup:
    agent.close()
  }

  def "content is sent as MSGPACK"() {
    setup:
    def requestContentType = new AtomicReference<MediaType>()
    def requestHeaders = new AtomicReference<Headers>()
    def requestBody = new AtomicReference<byte[]>()
    def agent = ratpack {
      handlers {
        put("v0.4/traces") {
          requestContentType.set(request.contentType)
          requestHeaders.set(request.headers)
          request.body.then {
            requestBody.set(it.bytes)
            response.send()
          }
        }
        put("v0.4/services") {
          response.status(200).send()
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendTraces(traces)
    requestContentType.get().type == "application/msgpack"
    requestHeaders.get().get("Datadog-Meta-Lang") == "java"
    requestHeaders.get().get("Datadog-Meta-Lang-Version") == System.getProperty("java.version", "unknown")
    requestHeaders.get().get("Datadog-Meta-Tracer-Version") == "Stubbed-Test-Version"
    convertList(requestBody.get()) == expectedRequestBody

    cleanup:
    agent.close()

    // Populate thread info dynamically as it is different when run via gradle vs idea.
    where:
    traces                                                               | expectedRequestBody
    []                                                                   | []
    [SpanFactory.newSpanOf(1L).setTag("service.name", "my-service")]     | [new TreeMap<>([
      "duration" : 0,
      "error"    : 0,
      "meta"     : ["thread.name": Thread.currentThread().getName(), "thread.id": "${Thread.currentThread().id}"],
      "name"     : "fakeOperation",
      "parent_id": 0,
      "resource" : "fakeResource",
      "service"  : "my-service",
      "span_id"  : 1,
      "start"    : 1000,
      "trace_id" : 1,
      "type"     : "fakeType"
    ])]
    [SpanFactory.newSpanOf(100L).setTag("resource.name", "my-resource")] | [new TreeMap<>([
      "duration" : 0,
      "error"    : 0,
      "meta"     : ["thread.name": Thread.currentThread().getName(), "thread.id": "${Thread.currentThread().id}"],
      "name"     : "fakeOperation",
      "parent_id": 0,
      "resource" : "my-resource",
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
        put("v0.4/traces") {
          response.status(200).send()
        }
        put("v0.4/services") {
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
        put("v0.4/traces") {
          response.status(200).send()
        }
        put("v0.4/services") {
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

  def "services content is sent as MSGPACK"() {
    setup:
    def requestContentType = new AtomicReference<MediaType>()
    def requestHeaders = new AtomicReference<Headers>()
    def requestBody = new AtomicReference<byte[]>()
    def agent = ratpack {
      handlers {
        put("v0.4/traces") {
          response.status(200).send()
        }
        put("v0.4/services") {
          requestContentType.set(request.contentType)
          requestHeaders.set(request.headers)
          request.body.then {
            requestBody.set(it.bytes)
            response.send()
          }
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)

    expect:
    client.sendServices(services)
    requestContentType.get().type == "application/msgpack"
    requestHeaders.get().get("Datadog-Meta-Lang") == "java"
    requestHeaders.get().get("Datadog-Meta-Lang-Version") == System.getProperty("java.version", "unknown")
    requestHeaders.get().get("Datadog-Meta-Tracer-Version") == "Stubbed-Test-Version"
    convertMap(requestBody.get()) == expectedRequestBody

    cleanup:
    agent.close()

    // Populate thread info dynamically as it is different when run via gradle vs idea.
    where:
    services                                                                                   | expectedRequestBody
    [:]                                                                                        | [:]
    ["my-service-name": new Service("my-service-name", "my-app-name", Service.AppType.CUSTOM)] | ["my-service-name": new TreeMap<>([
      "app"     : "my-app-name",
      "app_type": "custom"])
    ]
  }

  def "Api ResponseListeners see 200 responses"() {
    setup:
    def agentResponse = new AtomicReference<String>(null)
    ResponseListener responseListener = new ResponseListener() {
      @Override
      void onResponse(String endpoint, JsonNode responseJson) {
        agentResponse.set(responseJson.toString())
      }
    }
    boolean servicesAvailable = true
    def agent = ratpack {
      handlers {
        put("v0.4/traces") {
          response.status(200).send('{"hello":"test"}')
        }
        put("v0.4/services") {
          if (servicesAvailable) {
            response.status(200).send('{"service-response":"from-test"}')
          } else {
            response.status(404).send('{"service-response":"from-test"}')
          }
        }
      }
    }
    def client = new DDApi("localhost", agent.address.port)
    client.addResponseListener(responseListener)
    def services = ["my-service-name": new Service("my-service-name", "my-app-name", Service.AppType.CUSTOM)]

    when:
    client.sendTraces([])
    then:
    agentResponse.get() == '{"hello":"test"}'

    when:
    servicesAvailable = false
    agentResponse.set('not-set')
    client.sendServices(services)
    then:
    // response not seen because of non-200 status
    agentResponse.get() == 'not-set'


    cleanup:
    agent.close()
  }

    def "Api Downgrades to v3"() {
    setup:
    def v3Agent = ratpack {
      handlers {
        put("v0.3/traces") {
          response.status(200).send()
        }
        put("v0.3/services") {
          response.status(200).send()
        }
      }
    }
    def client = new DDApi("localhost", v3Agent.address.port)

    expect:
    client.sendTraces([])
    client.sendServices()

    cleanup:
    v3Agent.close()
  }

  static List<TreeMap<String, Object>> convertList(byte[] bytes) {
    return mapper.readValue(bytes, new TypeReference<List<TreeMap<String, Object>>>() {})
  }

  static TreeMap<String, Object> convertMap(byte[] bytes) {
    return mapper.readValue(bytes, new TypeReference<TreeMap<String, Object>>() {})
  }
}
