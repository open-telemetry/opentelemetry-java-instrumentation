package test

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.embedded.LocalServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.util.NestedServletException

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpringBootBasedTest extends AgentTestRunner {

  @LocalServerPort
  private int port

  @Autowired
  private TestRestTemplate restTemplate

  def "valid response"() {
    expect:
    port != 0
    restTemplate.getForObject("http://localhost:$port/", String) == "Hello World"

    and:
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
  }

  def "generates spans"() {
    expect:
    restTemplate.getForObject("http://localhost:$port/param/asdf1234/", String) == "Hello asdf1234"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "servlet.request"
    span.context().resourceName == "GET /param/{parameter}/"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    !span.context().getErrorFlag()
    span.context().parentId == 0
    span.context().tags["http.url"] == "http://localhost:$port/param/asdf1234/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["span.type"] == "web"
    span.context().tags["component"] == "java-web-servlet"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
    span.context().tags.size() == 8
  }

  def "generates 404 spans"() {
    def response = restTemplate.getForObject("http://localhost:$port/invalid", Map)
    expect:
    response.get("status") == 404
    response.get("error") == "Not Found"
    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.size() == 2

    and: // trace 0
    def trace0 = TEST_WRITER.get(0)
    trace0.size() == 1
    def span0 = trace0[0]

    span0.context().operationName == "servlet.request"
    span0.context().resourceName == "404"
    span0.context().spanType == DDSpanTypes.WEB_SERVLET
    !span0.context().getErrorFlag()
    span0.context().parentId == 0
    span0.context().tags["http.url"] == "http://localhost:$port/invalid"
    span0.context().tags["http.method"] == "GET"
    span0.context().tags["span.kind"] == "server"
    span0.context().tags["span.type"] == "web"
    span0.context().tags["component"] == "java-web-servlet"
    span0.context().tags["http.status_code"] == 404
    span0.context().tags["thread.name"] != null
    span0.context().tags["thread.id"] != null
    span0.context().tags.size() == 8

    and: // trace 1
    def trace1 = TEST_WRITER.get(1)
    trace1.size() == 1
    def span1 = trace1[0]

    span1.context().operationName == "servlet.request"
    span1.context().resourceName == "404"
    span1.context().spanType == DDSpanTypes.WEB_SERVLET
    !span1.context().getErrorFlag()
    span1.context().parentId == 0
    span1.context().tags["http.url"] == "http://localhost:$port/error"
    span1.context().tags["http.method"] == "GET"
    span1.context().tags["span.kind"] == "server"
    span1.context().tags["span.type"] == "web"
    span1.context().tags["component"] == "java-web-servlet"
    span1.context().tags["http.status_code"] == 404
    span1.context().tags["thread.name"] != null
    span1.context().tags["thread.id"] != null
    span1.context().tags.size() == 8
  }

  def "generates error spans"() {
    expect:
    def response = restTemplate.getForObject("http://localhost:$port/error/qwerty/", Map)
    response.get("status") == 500
    response.get("error") == "Internal Server Error"
    response.get("exception") == "java.lang.RuntimeException"
    response.get("message") == "qwerty"
    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.size() == 2

    and: // trace 0
    def trace0 = TEST_WRITER.get(0)
    trace0.size() == 1
    def span0 = trace0[0]

    span0.context().operationName == "servlet.request"
    span0.context().resourceName == "GET /error/{parameter}/"
    span0.context().spanType == DDSpanTypes.WEB_SERVLET
    span0.context().getErrorFlag()
    span0.context().parentId == 0
    span0.context().tags["http.url"] == "http://localhost:$port/error/qwerty/"
    span0.context().tags["http.method"] == "GET"
    span0.context().tags["span.kind"] == "server"
    span0.context().tags["span.type"] == "web"
    span0.context().tags["component"] == "java-web-servlet"
    span0.context().tags["http.status_code"] == 500
    span0.context().tags["thread.name"] != null
    span0.context().tags["thread.id"] != null
    span0.context().tags["error"] == true
    span0.context().tags["error.msg"] == "Request processing failed; nested exception is java.lang.RuntimeException: qwerty"
    span0.context().tags["error.type"] == NestedServletException.getName()
    span0.context().tags["error.stack"] != null
    span0.context().tags.size() == 12

    and: // trace 1
    def trace1 = TEST_WRITER.get(1)
    trace1.size() == 1
    def span1 = trace1[0]

    span1.context().operationName == "servlet.request"
    span1.context().resourceName == "GET /error"
    span1.context().spanType == DDSpanTypes.WEB_SERVLET
    !span1.context().getErrorFlag()
    span1.context().parentId == 0
    span1.context().tags["http.url"] == "http://localhost:$port/error"
    span1.context().tags["http.method"] == "GET"
    span1.context().tags["span.kind"] == "server"
    span1.context().tags["span.type"] == "web"
    span1.context().tags["component"] == "java-web-servlet"
    span1.context().tags["http.status_code"] == 500
    span1.context().tags["thread.name"] != null
    span1.context().tags["thread.id"] != null
    span1.context().tags.size() == 8
  }

  def "validated form"() {
    expect:
    restTemplate.postForObject("http://localhost:$port/validated", new TestForm("bob", 20), String) == "Hello bob Person(Name: bob, Age: 20)"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "servlet.request"
    span.context().resourceName == "POST /validated"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    !span.context().getErrorFlag()
    span.context().parentId == 0
    span.context().tags["http.url"] == "http://localhost:$port/validated"
    span.context().tags["http.method"] == "POST"
    span.context().tags["span.kind"] == "server"
    span.context().tags["span.type"] == "web"
    span.context().tags["component"] == "java-web-servlet"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
    span.context().tags.size() == 8
  }

  def "invalid form"() {
    expect:
    def response = restTemplate.postForObject("http://localhost:$port/validated", new TestForm("bill", 5), Map, Map)
    response.get("status") == 400
    response.get("error") == "Bad Request"
    response.get("exception") == "org.springframework.web.bind.MethodArgumentNotValidException"
    response.get("message") == "Validation failed for object='testForm'. Error count: 1"
    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.size() == 2

    and: // trace 0
    def trace0 = TEST_WRITER.get(0)
    trace0.size() == 1
    def span0 = trace0[0]

    span0.context().operationName == "servlet.request"
    span0.context().resourceName == "POST /validated"
    span0.context().spanType == DDSpanTypes.WEB_SERVLET
    span0.context().getErrorFlag()
    span0.context().parentId == 0
    span0.context().tags["http.url"] == "http://localhost:$port/validated"
    span0.context().tags["http.method"] == "POST"
    span0.context().tags["span.kind"] == "server"
    span0.context().tags["span.type"] == "web"
    span0.context().tags["component"] == "java-web-servlet"
    span0.context().tags["http.status_code"] == 400
    span0.context().tags["thread.name"] != null
    span0.context().tags["thread.id"] != null
    span0.context().tags["error"] == true
    span0.context().tags["error.msg"].toString().startsWith("Validation failed")
    span0.context().tags["error.type"] == MethodArgumentNotValidException.getName()
    span0.context().tags["error.stack"] != null
    span0.context().tags.size() == 12

    and: // trace 1
    def trace1 = TEST_WRITER.get(1)
    trace1.size() == 1
    def span1 = trace1[0]

    span1.context().operationName == "servlet.request"
    span1.context().resourceName == "POST /error"
    span1.context().spanType == DDSpanTypes.WEB_SERVLET
    !span1.context().getErrorFlag()
    span1.context().parentId == 0
    span1.context().tags["http.url"] == "http://localhost:$port/error"
    span1.context().tags["http.method"] == "POST"
    span1.context().tags["span.kind"] == "server"
    span1.context().tags["span.type"] == "web"
    span1.context().tags["component"] == "java-web-servlet"
    span1.context().tags["http.status_code"] == 400
    span1.context().tags["thread.name"] != null
    span1.context().tags["thread.id"] != null
    span1.context().tags.size() == 8
  }
}
