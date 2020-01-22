import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpClientDecorator
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared

class AkkaHttpClientInstrumentationTest extends HttpClientTest<AkkaHttpClientDecorator> {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })

    def response = Http.get(system)
      .singleRequest(request, materializer)
    //.whenComplete { result, error ->
    // FIXME: Callback should be here instead.
    //  callback?.call()
    //}
      .toCompletableFuture()
      .get()
    callback?.call()
    return response.status().intValue()
  }

  @Override
  AkkaHttpClientDecorator decorator() {
    return AkkaHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "akka-http.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  def "singleRequest exception trace"() {
    when:
    // Passing null causes NPE in singleRequest
    Http.get(system).singleRequest(null, materializer)

    then:
    thrown NullPointerException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parent()
          operationName "akka-http.request"
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "akka-http-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(NullPointerException)
          }
        }
      }
    }

    where:
    renameService << [false, true]
  }
}
