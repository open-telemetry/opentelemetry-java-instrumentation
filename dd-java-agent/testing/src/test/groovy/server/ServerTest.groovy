package server

import datadog.opentracing.DDTracer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.common.writer.ListWriter
import okhttp3.MultipartBody
import okhttp3.Request
import spock.lang.Shared

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

/* Don't actually need AgentTestRunner, but it messes up the classloader for AgentTestRunnerTest if this runs first. */

class ServerTest extends AgentTestRunner {
  @Shared
  def client = OkHttpUtils.client()

  def "test server lifecycle"() {
    setup:
    def server = httpServer(startAutomatically) {
      handlers {}
    }

    expect:
    server.internalServer.isRunning() == startAutomatically

    when:
    server.start()

    then:
    server.internalServer.isRunning()

    when:
    server.stop()

    then:
    !server.internalServer.isRunning()

    when:
    server.start()

    then:
    server.internalServer.isRunning()

    when:
    server.stop()

    then:
    !server.internalServer.isRunning()

    cleanup:
    server.stop()

    where:
    startAutomatically << [true, false]
  }

  def "server 404's with no handlers"() {
    setup:
    def server = httpServer {
      handlers {}
    }

    when:
    def request = new Request.Builder()
      .url("$server.address")
      .get()
      .build()
    def response = client.newCall(request).execute()

    then:
    response.code() == 404
    response.body().string().contains("<title>Error 404 Not Found</title>")

    cleanup:
    server.stop()
  }

  def "server accepts #path requests"() {
    setup:
    def server = httpServer {
      handlers {
        get("/get") {
          response.send("/get response")
        }
        post("/post") {
          response.send("/post response")
        }
        put("/put") {
          response.send("/put response")
        }
        prefix("/base") {
          response.send("${request.path} response")
        }
        all {
          response.send("/all response")
        }
      }
    }

    when:
    def request = new Request.Builder()
      .url("$server.address/$path")

    if (method == "get") {
      request."$method"()
    } else {
      request."$method"(body())
    }

    def response = client.newCall(request.build()).execute()

    then:
    response.code() == 200
    response.body().string().trim() == "/$path response"

    cleanup:
    server.stop()

    where:
    method | path
    "get"  | "get"
    "post" | "post"
    "put"  | "put"
    "get"  | "base"
    "post" | "base"
    "get"  | "base/get"
    "post" | "base/post"
    "get"  | "all"
    "post" | "all"
  }

  def "server returns different response codes"() {
    setup:
    def server = httpServer {
      handlers {
        get("/get") {
          response.status(201).send("get response")
        }
        post("/post") {
          response.status(202).send("post response")
        }
        all {
          response.status(203).send("all response")
        }
      }
    }

    when:
    def request = new Request.Builder()
      .url("$server.address/$method")

    if (method == "post") {
      request."$method"(body())
    } else {
      request."$method"()
    }

    def response = client.newCall(request.build()).execute()

    then:
    response.code() == code
    response.body().string().trim() == "$resp"
    server.lastRequest.contentType == contentType
    server.lastRequest.text.empty == !hasContent

    cleanup:
    server.stop()

    where:
    method | resp            | code | contentType       | hasContent
    "get"  | "get response"  | 201  | null              | false
    "post" | "post response" | 202  | "multipart/mixed" | true
    "head" | ""              | 203  | null              | false
  }

  def "server retains details of request sent"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          response.send()
        }
      }
    }

    when:
    def request = new Request.Builder()
      .url("$server.address")
      .get()
      .header(headerKey, headerValue)
      .build()

    def response = client.newCall(request).execute()

    then:
    response.code() == 200
    response.body().string().trim() == ""

    server.lastRequest.contentType == null
    server.lastRequest.headers.get(headerKey) == headerValue
    server.lastRequest.headers.get(newKey) == null

    when:
    def request2 = new Request.Builder()
      .url("$server.address")
      .get()
      .header(newKey, newValue)
      .build()

    def response2 = client.newCall(request2).execute()

    then:
    response2.code() == 200
    response2.body().string().trim() == ""

    server.lastRequest.contentType == null
    server.lastRequest.headers.get(headerKey) == null
    server.lastRequest.headers.get(newKey) == newValue

    cleanup:
    server.stop()

    where:
    headerKey = "some-key"
    headerValue = "some-value"
    newKey = "new-key"
    newValue = "new-value"
  }

  def "server redirect"() {
    setup:
    client = OkHttpUtils.clientBuilder().followRedirects(followRedirects).build()
    def server = httpServer {
      handlers {
        get("/redirect") {
          redirect("/redirected")
        }
        get("/redirected") {
          response.status(201).send("somewhere else")
        }
      }
    }

    when:
    def request = new Request.Builder()
      .url("$server.address/redirect")
      .get()
      .build()
    def response = client.newCall(request).execute()

    then:
    response.code() == code
    response.body().string() == body

    cleanup:
    server.stop()

    where:
    followRedirects | code | body
    true            | 201  | "somewhere else"
    false           | 302  | ""
  }

  def "server sends no body on head request"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          response.send("invalid content")
        }
      }
    }

    when:
    def request = new Request.Builder()
      .url("$server.address")
      .head()
      .build()

    def response = client.newCall(request).execute()

    then:
    response.code() == 200
    response.body().string().trim() == ""

    cleanup:
    server.stop()
  }

  def "server handles distributed request"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          handleDistributedRequest()
          response.send("done")
        }
      }
    }
    def writer = new ListWriter()
    server.tracer = new DDTracer(writer)

    when:
    def request = new Request.Builder()
      .url("$server.address")
      .get()
      .build()

    def response = client.newCall(request).execute()

    then:
    response.code() == 200
    response.body().string().trim() == "done"

    ListWriterAssert.assertTraces(writer, 1) {
      server.distributedRequestTrace(it, 0)
    }

    cleanup:
    server.stop()
  }

  def body() {
    return new MultipartBody.Builder().addFormDataPart("key", "value").build()
  }
}
