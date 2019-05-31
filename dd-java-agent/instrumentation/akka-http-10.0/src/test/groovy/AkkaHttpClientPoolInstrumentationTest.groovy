import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.headers.RawHeader
import akka.japi.Pair
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.akkahttp.AkkaHttpClientDecorator
import scala.util.Try
import spock.lang.Shared

class AkkaHttpClientPoolInstrumentationTest extends HttpClientTest<AkkaHttpClientDecorator> {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  def pool = Http.get(system).superPool(materializer)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })

    def response = Source
      .<Pair<HttpRequest, Integer>> single(new Pair(request, 1))
      .via(pool)
      .runWith(Sink.<Pair<Try<HttpResponse>, Integer>> head(), materializer)
      .toCompletableFuture().get().first().get()
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

  boolean testRedirects() {
    false
  }
}
