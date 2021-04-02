/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import play.libs.ws.StandaloneWSClient
import play.libs.ws.StandaloneWSRequest
import play.libs.ws.StandaloneWSResponse
import play.libs.ws.ahc.StandaloneAhcWSClient
import scala.Function1
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class PlayJavaWsClientTest extends PlayWsClientTestBase {
  @Shared
  StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    return sendRequest(method, uri, headers).toCompletableFuture().get().status
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    sendRequest(method, uri, headers).thenAccept {
      callback.accept(it.status)
    }
  }

  private CompletionStage<StandaloneWSResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    return wsRequest.setMethod(method).execute()
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
  int doRequest(String method, URI uri, Map<String, String> headers) {
    return sendRequest(method, uri, headers).toCompletableFuture().get().status
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    sendRequest(method, uri, headers).thenAccept {
      callback.accept(it.status)
    }
  }

  private CompletionStage<StandaloneWSResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    CompletionStage<StandaloneWSResponse> stream = wsRequest.setMethod(method).stream()
    // The status can be ready before the body so explicitly call wait for body to be ready
    return stream
      .thenCompose {StandaloneWSResponse response ->
        response.getBodyAsSource().runFold("", { acc, out -> "" }, materializer)
      }
      .thenCombine(stream) {String body, StandaloneWSResponse response ->
        response
      }
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
  int doRequest(String method, URI uri, Map<String, String> headers) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = sendRequest(method, uri, headers)
    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))
    return wsResponse.status()
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = sendRequest(method, uri, headers)
    futureResponse.onComplete(new Function1<Try<play.api.libs.ws.StandaloneWSResponse>, Void>() {
      @Override
      Void apply(Try<play.api.libs.ws.StandaloneWSResponse> response) {
        callback.accept(response.get().status())
        return null
      }

      @Override
      <A> Function1<A, Void> compose(Function1<A, Try<play.api.libs.ws.StandaloneWSResponse>> g) {
        throw new UnsupportedOperationException()
      }

      @Override
      <A> Function1<Try<play.api.libs.ws.StandaloneWSResponse>, A> andThen(Function1<Void, A> g) {
        throw new UnsupportedOperationException()
      }
    }, ExecutionContext.global())
  }

  private Future<play.api.libs.ws.StandaloneWSResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    return wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .execute()
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
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = sendRequest(method, uri, headers)
    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))
    return wsResponse.status()
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = sendRequest(method, uri, headers)
    futureResponse.onComplete(new Function1<Try<play.api.libs.ws.StandaloneWSResponse>, Void>() {
      @Override
      Void apply(Try<play.api.libs.ws.StandaloneWSResponse> response) {
        callback.accept(response.get().status())
        return null
      }

      @Override
      <A> Function1<A, Void> compose(Function1<A, Try<play.api.libs.ws.StandaloneWSResponse>> g) {
        throw new UnsupportedOperationException()
      }

      @Override
      <A> Function1<Try<play.api.libs.ws.StandaloneWSResponse>, A> andThen(Function1<Void, A> g) {
        throw new UnsupportedOperationException()
      }
    }, ExecutionContext.global())
  }

  private Future<play.api.libs.ws.StandaloneWSResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .stream()
    // The status can be ready before the body so explicitly call wait for body to be ready
    Future<String> bodyResponse = futureResponse.flatMap(new Function1<play.api.libs.ws.StandaloneWSResponse, Future<String>>() {
      @Override
      Future<String> apply(play.api.libs.ws.StandaloneWSResponse wsResponse) {
        return wsResponse.bodyAsSource().runFold("", { acc, out -> "" }, materializer)
      }

      @Override
      <A> Function1<A, Future<String>> compose(Function1<A, play.api.libs.ws.StandaloneWSResponse> g) {
        throw new UnsupportedOperationException()
      }

      @Override
      <A> Function1<play.api.libs.ws.StandaloneWSResponse, A> andThen(Function1<Future<String>, A> g) {
        throw new UnsupportedOperationException()
      }
    }, ExecutionContext.global())
    return bodyResponse.flatMap(new Function1<String, Future<play.api.libs.ws.StandaloneWSResponse>>() {
      @Override
      Future<play.api.libs.ws.StandaloneWSResponse> apply(String v1) {
        return futureResponse
      }

      @Override
      <A> Function1<A, Future<play.api.libs.ws.StandaloneWSResponse>> compose(Function1<A, String> g) {
        throw new UnsupportedOperationException()
      }

      @Override
      <A> Function1<String, A> andThen(Function1<Future<play.api.libs.ws.StandaloneWSResponse>, A> g) {
        throw new UnsupportedOperationException()
      }
    }, ExecutionContext.global())
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}
