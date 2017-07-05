package com.datadoghq.trace.writer

import com.datadog.trace.SpanFactory
import com.datadoghq.trace.DDSpan
import com.fasterxml.jackson.databind.ObjectMapper
import ratpack.http.MediaType
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack
import static ratpack.http.MediaType.APPLICATION_JSON

class DDApiTest extends Specification {
    static def mapper = new ObjectMapper()

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
        def requestBody = new AtomicReference<String>()
        def agent = ratpack {
            handlers {
                put("v0.3/traces") {
                    requestContentType.set(request.contentType)
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
        areEqual(requestBody.get(), expectedRequestBody)

        cleanup:
        agent.close()

        where:
        traces                      | expectedRequestBody
        []                          | '[]'
        [SpanFactory.newSpanOf(1L)] | '''[{
            "duration":0,
            "error":0,
            "meta":{"thread-name":"main","thread-id":"1"},
            "name":"fakeOperation",
            "parent_id":0,
            "resource":"fakeResource"
            "service":"fakeService",
            "span_id":1,
            "start":1000,
            "trace_id":1,
            "type":"fakeType",
        }]'''
        [SpanFactory.newSpanOf(100L)] | '''[{
            "duration":0,
            "error":0,
            "meta":{"thread-name":"main","thread-id":"1"},
            "name":"fakeOperation",
            "parent_id":0,
            "resource":"fakeResource"
            "service":"fakeService",
            "span_id":1,
            "start":100000,
            "trace_id":1,
            "type":"fakeType",
        }]'''
    }


    static void areEqual(String json1, String json2) {
        def tree1 = mapper.readTree json1
        def tree2 = mapper.readTree json2

        assert tree1.equals(tree2)
    }
}
