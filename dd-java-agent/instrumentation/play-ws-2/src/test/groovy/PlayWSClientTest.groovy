import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.playws.PlayWSClientDecorator
import play.libs.ws.StandaloneWSClient
import play.libs.ws.StandaloneWSRequest
import play.libs.ws.StandaloneWSResponse
import play.libs.ws.ahc.StandaloneAhcWSClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class PlayWSClientTest extends HttpClientTest<PlayWSClientDecorator> {
  @Shared
  ActorSystem system

  @Shared
  StandaloneWSClient wsClient

  def setupSpec() {
    String name = "play-ws"
    system = ActorSystem.create(name)
    ActorMaterializerSettings settings = ActorMaterializerSettings.create(system)
    ActorMaterializer materializer = ActorMaterializer.create(settings, system, name)

    AsyncHttpClientConfig asyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxRequestRetry(0)
        .setShutdownQuietPeriod(0)
        .setShutdownTimeout(0)
        .build()

    AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)

    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
    system?.terminate()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    StandaloneWSResponse wsResponse = wsRequest.execute(method)
      .whenComplete({ response, throwable ->
        callback?.call()
      }).toCompletableFuture().get(5, TimeUnit.SECONDS)

    return wsResponse.getStatus()
  }

  @Override
  PlayWSClientDecorator decorator() {
    return PlayWSClientDecorator.DECORATE
  }

  String expectedOperationName() {
    return "play-ws.request"
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }
}
