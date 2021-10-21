/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import play.shaded.ahc.io.netty.resolver.InetNameResolver
import play.shaded.ahc.io.netty.util.concurrent.EventExecutor
import play.shaded.ahc.io.netty.util.concurrent.ImmediateEventExecutor
import play.shaded.ahc.io.netty.util.concurrent.Promise
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase
import spock.lang.Shared

abstract class PlayWsClientTestBaseBase<REQUEST> extends HttpClientTest<REQUEST> implements AgentTestTrait {
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

    // Replace dns name resolver with custom implementation that returns only once address for each
    // host. This is needed for "connection error dropped request" because in case of connection
    // failure ahc will try the next address which isn't necessary for this test.
    RequestBuilderBase.DEFAULT_NAME_RESOLVER = new CustomNameResolver(ImmediateEventExecutor.INSTANCE)

    AsyncHttpClientConfig asyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxRequestRetry(0)
        .setShutdownQuietPeriod(0)
        .setShutdownTimeout(0)
        .setMaxRedirects(3)
        .setConnectTimeout(CONNECT_TIMEOUT_MS)
        .build()

    asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
  }

  def cleanupSpec() {
    system?.terminate()
  }

  @Override
  int maxRedirects() {
    3
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    def attributes = super.httpAttributes(uri) + extra
    if (uri.toString().endsWith("/circular-redirect")) {
      attributes.remove(SemanticAttributes.NET_PEER_NAME)
      attributes.remove(SemanticAttributes.NET_PEER_PORT)
    }
    return attributes
  }
}

class CustomNameResolver extends InetNameResolver {
  CustomNameResolver(EventExecutor executor) {
    super(executor)
  }

  protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
    try {
      promise.setSuccess(InetAddress.getByName(inetHost))
    } catch (UnknownHostException exception) {
      promise.setFailure(exception)
    }
  }

  protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
    try {
      // default implementation calls InetAddress.getAllByName
      promise.setSuccess(Collections.singletonList(InetAddress.getByName(inetHost)))
    } catch (UnknownHostException exception) {
      promise.setFailure(exception)
    }
  }
}
