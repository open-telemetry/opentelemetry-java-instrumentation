/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import play.libs.ws.StandaloneWSClient
import play.libs.ws.StandaloneWSRequest
import play.libs.ws.StandaloneWSResponse
import play.libs.ws.ahc.StandaloneAhcWSClient
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class PlayJavaWsClientTest extends PlayWsClientTestBase {
  @Shared
  StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    StandaloneWSResponse wsResponse = wsRequest.setMethod(method).execute()
      .toCompletableFuture().get(5, TimeUnit.SECONDS)
    callback?.accept(wsResponse.getStatus())
    return wsResponse.getStatus()
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

@Timeout(5)
class PlayJavaStreamedWsClientTest extends PlayWsClientTestBase {
  @Shared
  StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    StandaloneWSResponse wsResponse = wsRequest.setMethod(method).stream()
      .toCompletableFuture().get(5, TimeUnit.SECONDS)

    // The status can be ready before the body so explicitly call wait for body to be ready
    wsResponse.getBodyAsSource().runFold("", { acc, out -> "" }, materializer)
      .toCompletableFuture().get(5, TimeUnit.SECONDS)
    callback?.accept(wsResponse.getStatus())
    return wsResponse.getStatus()
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

@Timeout(5)
class PlayScalaWsClientTest extends PlayWsClientTestBase {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .execute()

    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))

    callback?.accept(wsResponse.status())
    return wsResponse.status()
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

@Timeout(5)
class PlayScalaStreamedWsClientTest extends PlayWsClientTestBase {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .stream()

    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))

    // The status can be ready before the body so explicitly call wait for body to be ready
    Await.result(
      wsResponse.bodyAsSource().runFold("", { acc, out -> "" }, materializer),
      Duration.apply(5, TimeUnit.SECONDS))

    callback?.accept(wsResponse.status())
    return wsResponse.status()
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}
