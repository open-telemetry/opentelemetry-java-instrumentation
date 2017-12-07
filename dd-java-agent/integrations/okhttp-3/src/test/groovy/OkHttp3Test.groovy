import com.datadoghq.trace.DDTags
import com.datadoghq.trace.DDTracer
import com.datadoghq.trace.writer.ListWriter
import dd.test.TestUtils
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.http.Headers
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class OkHttp3Test extends Specification {

  @Shared
  def writer = new ListWriter()
  @Shared
  def tracer = new DDTracer(writer)

  def setupSpec() {
    TestUtils.addByteBuddyAgent()
    TestUtils.registerOrReplaceGlobalTracer(tracer)
  }

  def setup() {
    writer.start()
  }

  def "sending a request creates spans and sends headers"() {
    setup:
    def receivedHeaders = new AtomicReference<Headers>()
    def server = ratpack {
      handlers {
        all {
          receivedHeaders.set(request.headers)
          response.status(200).send("pong")
        }
      }
    }
    def client = new OkHttpClient()
    def request = new Request.Builder()
      .url("http://localhost:$server.address.port/ping")
      .build()

    def response = client.newCall(request).execute()

    expect:
    response.body.string() == "pong"
    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 2

    and: // span 0
    def span1 = trace[0]

    span1.context().operationName == "GET"
    span1.serviceName == "unnamed-java-app"
    span1.resourceName == "GET"
    span1.type == null
    !span1.context().getErrorFlag()
    span1.context().parentId == 0


    def tags1 = span1.context().tags
    tags1["component"] == "okhttp"
    tags1["thread.name"] != null
    tags1["thread.id"] != null
    tags1.size() == 3

    and: // span 1
    def span2 = trace[1]

    span2.context().operationName == "GET"
    span2.serviceName == "unnamed-java-app"
    span2.resourceName == "GET"
    span2.type == null
    !span2.context().getErrorFlag()
    span2.context().parentId == span1.spanId


    def tags2 = span2.context().tags
    tags2[Tags.COMPONENT.key] == "okhttp"
    tags2[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_CLIENT
    tags2[Tags.HTTP_METHOD.key] == "GET"
    tags2[Tags.HTTP_URL.key] == "http://localhost:$server.address.port/ping"
    tags2[Tags.PEER_HOSTNAME.key] == "localhost"
    tags2[Tags.PEER_PORT.key] == server.address.port
    tags2[Tags.PEER_HOST_IPV4.key] != null
    tags2[DDTags.THREAD_NAME] != null
    tags2[DDTags.THREAD_ID] != null
    tags2.size() == 10

    receivedHeaders.get().get("x-datadog-trace-id") == "$span2.traceId"
    receivedHeaders.get().get("x-datadog-parent-id") == "$span2.spanId"

    cleanup:
    server.close()
  }
}
