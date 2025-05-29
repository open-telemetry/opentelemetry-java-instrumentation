/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.OS;
import ratpack.exec.Operation;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpClientSpec;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.exec.ExecHarness;

public abstract class AbstractRatpackHttpClientTest extends AbstractHttpClientTest<Void> {

  protected final ExecHarness exec = ExecHarness.harness();

  protected HttpClient client;
  protected HttpClient singleConnectionClient;

  @BeforeAll
  protected void setUpClient() throws Exception {
    exec.run(
        unused -> {
          client = buildHttpClient();
          singleConnectionClient = buildHttpClient(spec -> spec.poolSize(1));
        });
  }

  @AfterAll
  void cleanUpClient() {
    client.close();
    singleConnectionClient.close();
    exec.close();
  }

  protected HttpClient buildHttpClient() throws Exception {
    return buildHttpClient(Action.noop());
  }

  protected HttpClient buildHttpClient(Action<? super HttpClientSpec> action) throws Exception {
    return HttpClient.of(action);
  }

  @Override
  public Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null;
  }

  @Override
  public int sendRequest(Void request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return exec.yield(execution -> internalSendRequest(client, method, uri, headers))
        .getValueOrThrow();
  }

  @Override
  public final void sendRequestWithCallback(
      Void request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult)
      throws Exception {
    exec.yield(
            (e) ->
                Operation.of(
                        () ->
                            internalSendRequest(client, method, uri, headers)
                                .result(
                                    result ->
                                        httpClientResult.complete(
                                            result::getValue, result.getThrowable())))
                    .promise())
        .getValueOrThrow();
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
              spec.connectTimeout(Duration.ofSeconds(2));
              if (uri.getPath().equals("/read-timeout")) {
                spec.readTimeout(readTimeout());
              }
              spec.method(method);
              spec.headers(headersSpec -> headers.forEach(headersSpec::add));
            });

    return resp.map(ReceivedResponse::getStatusCode);
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setSingleConnectionFactory(
        (host, port) ->
            (path, headers) -> {
              URI uri = resolveAddress(path);
              return exec.yield(
                      unused -> internalSendRequest(singleConnectionClient, "GET", uri, headers))
                  .getValueOrThrow();
            });

    if (useNettyClientAttributes()) {
      optionsBuilder.setExpectedClientSpanNameMapper(
          AbstractRatpackHttpClientTest::nettyExpectedClientSpanNameMapper);
    }

    optionsBuilder.setClientSpanErrorMapper(
        AbstractRatpackHttpClientTest::nettyClientSpanErrorMapper);

    optionsBuilder.setHttpAttributes(this::computeHttpAttributes);

    optionsBuilder.disableTestRedirects();
    // these tests will pass, but they don't really test anything since REQUEST is Void
    optionsBuilder.disableTestReusedRequest();

    optionsBuilder.spanEndsAfterBody();
  }

  protected Set<AttributeKey<?>> computeHttpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return Collections.emptySet();
      default:
        HashSet<AttributeKey<?>> attributes =
            new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
        if (useNettyClientAttributes()) {
          // underlying netty instrumentation does not provide these
          attributes.remove(SERVER_ADDRESS);
          attributes.remove(SERVER_PORT);
        } else {
          // ratpack client instrumentation does not provide this
          attributes.remove(NETWORK_PROTOCOL_VERSION);
        }
        return attributes;
    }
  }

  protected boolean useNettyClientAttributes() {
    return true;
  }

  private static Throwable nettyClientSpanErrorMapper(URI uri, Throwable exception) {
    if (uri.toString().equals("https://192.0.2.1/")) {
      return new ConnectTimeoutException(
          "connection timed out"
              + (Boolean.getBoolean("testLatestDeps") ? " after 2000 ms" : "")
              + ": /192.0.2.1:443");
    } else if (OS.WINDOWS.isCurrentOs() && uri.toString().equals("http://localhost:61/")) {
      return new ConnectTimeoutException("connection timed out: localhost/127.0.0.1:61");
    } else if (uri.getPath().equals("/read-timeout")) {
      return ReadTimeoutException.INSTANCE;
    }
    return exception;
  }

  private static String nettyExpectedClientSpanNameMapper(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }
}
