/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack

import com.google.common.collect.ImmutableList
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.OS
import ratpack.exec.Operation
import ratpack.exec.Promise
import ratpack.exec.internal.DefaultExecController
import ratpack.func.Action
import ratpack.http.client.HttpClient
import ratpack.http.client.HttpClientSpec
import ratpack.http.client.ReceivedResponse
import ratpack.test.exec.ExecHarness

import java.time.Duration

abstract class AbstractRatpackHttpClientTest extends AbstractHttpClientTest<Void> {

  final ExecHarness exec = ExecHarness.harness()

  HttpClient client
  HttpClient singleConnectionClient

  @BeforeAll
  void setUpClient() throws Exception {
    exec.run {
        (exec.controller as DefaultExecController).setInitializers(ImmutableList.of(new OpenTelemetryExecInitializer()))
        (exec.controller as DefaultExecController).setInterceptors(ImmutableList.of(OpenTelemetryExecInterceptor.INSTANCE))
        client = buildHttpClient()
        singleConnectionClient = buildHttpClient(spec -> spec.poolSize(1))
      }
  }

  @AfterAll
  void cleanUpClient() {
    client.close()
    singleConnectionClient.close()
    exec.close()
  }

  protected HttpClient buildHttpClient() throws Exception {
    return buildHttpClient(Action.noop())
  }

  protected HttpClient buildHttpClient(Action<? super HttpClientSpec> action) throws Exception {
    return HttpClient.of(action)
  }

  @Override
  protected final Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  protected final int sendRequest(Void request, String method, URI uri, Map<String, String> headers)
    throws Exception {
    return exec.yield { internalSendRequest(client, method, uri, headers) }
      .getValueOrThrow()
  }

  @Override
  protected final void sendRequestWithCallback(
    Void request,
    String method,
    URI uri,
    Map<String, String> headers,
    RequestResult requestResult)
    throws Exception {
    exec.execute(
      Operation.of(
            () ->
      internalSendRequest(client, method, uri, headers)
        .result(
          result ->
                            requestResult.complete(result::getValue, result.getThrowable()))))
  }

  // overridden in RatpackForkedHttpClientTest
  protected Promise<Integer> internalSendRequest(
    HttpClient client, String method, URI uri, Map<String, String> headers) {
    Promise<ReceivedResponse> resp =
      client.request(
        uri,
        spec -> {
          // Connect timeout for the whole client was added in 1.5 so we need to add timeout for
          // each request
          spec.connectTimeout(Duration.ofSeconds(2))
          if (uri.getPath().equals("/read-timeout")) {
            spec.readTimeout(readTimeout())
          }
          spec.method(method)
          spec.headers(headersSpec -> headers.forEach(headersSpec::add))
        })

    return resp.map(ReceivedResponse::getStatusCode)
  }

  @Override
  protected void configure(HttpClientTestOptions options) {
    options.setSingleConnectionFactory(
      (host, port) ->
        (path, headers) -> {
              URI uri = resolveAddress(path)
          return exec.yield(
                      unused -> internalSendRequest(singleConnectionClient, "GET", uri, headers))
                  .getValueOrThrow()
        })

    options.setExpectedClientSpanNameMapper(
      (uri, method) -> {
        switch (uri.toString()) {
          case "http://localhost:61/": // unopened port
          case "https://192.0.2.1/": // non routable address
            return "CONNECT"
          default:
            return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(
              uri, method)
        }
      })

    options.setClientSpanErrorMapper(
      (uri, exception) -> {
        if (uri.toString() == "https://192.0.2.1/") {
          return new ConnectTimeoutException("connection timed out: /192.0.2.1:443")
        } else if (OS.WINDOWS.isCurrentOs() && uri.toString().equals("http://localhost:61/")) {
          return new ConnectTimeoutException("connection timed out: localhost/127.0.0.1:61")
        } else if (uri.getPath().equals("/read-timeout")) {
          return ReadTimeoutException.INSTANCE
        }
        return exception
      })

    options.setHttpAttributes(
      uri -> {
        switch (uri.toString()) {
          case "http://localhost:61/": // unopened port
          case "https://192.0.2.1/": // non routable address
            return Collections.emptySet()
          default:
            return HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES
        }
      })

    options.disableTestRedirects()

    // these tests will pass, but they don't really test anything since REQUEST is Void
    options.disableTestReusedRequest()

    options.enableTestReadTimeout()


    // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
    options.disableTestWithClientParent()
    // Agent users have automatic propagation through executor instrumentation, but library users
    // should do manually using Armeria patterns.
//    options.disableTestCallbackWithParent()
//    options.disableTestErrorWithCallback()
  }
}
