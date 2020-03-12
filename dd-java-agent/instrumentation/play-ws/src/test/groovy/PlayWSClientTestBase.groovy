import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.playws.PlayWSClientDecorator
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig
import spock.lang.Shared

abstract class PlayWSClientTestBase extends HttpClientTest {
  @Shared
  ActorSystem system

  @Shared
  AsyncHttpClient asyncHttpClient

  @Shared
  ActorMaterializer materializer

  def setupSpec() {
    String name = "play-ws"
    system = ActorSystem.create(name)
    ActorMaterializerSettings settings = ActorMaterializerSettings.create(system)
    materializer = ActorMaterializer.create(settings, system, name)

    AsyncHttpClientConfig asyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxRequestRetry(0)
        .setShutdownQuietPeriod(0)
        .setShutdownTimeout(0)
        .build()

    asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
  }

  def cleanupSpec() {
    system?.terminate()
  }

  @Override
  String component() {
    return PlayWSClientDecorator.DECORATE.component()
  }

  String expectedOperationName() {
    return "play-ws.request"
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  @Override
  boolean testCallbackWithParent() {
    return false
  }
}
