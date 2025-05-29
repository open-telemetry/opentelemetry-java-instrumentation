/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ConnectTimeoutException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInitializer;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInterceptor;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.OS;
import ratpack.exec.Operation;
import ratpack.exec.Promise;
import ratpack.exec.internal.DefaultExecController;
import ratpack.func.Action;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpClientReadTimeoutException;
import ratpack.http.client.HttpClientSpec;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.exec.ExecHarness;

abstract class AbstractRatpackHttpClientTest extends AbstractHttpClientTest<Void> {

  final ExecHarness exec = ExecHarness.harness();

  HttpClient client;
  HttpClient singleConnectionClient;

  @BeforeAll
  void setUpClient() throws Exception {
    exec.run(
        unused -> {
          ((DefaultExecController) exec.getController())
              .setInitializers(ImmutableList.of(OpenTelemetryExecInitializer.INSTANCE));
          ((DefaultExecController) exec.getController())
              .setInterceptors(ImmutableList.of(OpenTelemetryExecInterceptor.INSTANCE));
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
    return exec.yield(
            r -> r.add(Context.class, Context.current()),
            execution -> internalSendRequest(client, method, uri, headers))
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
    // ratpack-test 1.8 supports execute with an Action of registrySpec
    exec.yield(
            r -> r.add(Context.class, Context.current()),
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
                      r -> r.add(Context.class, Context.current()),
                      unused -> internalSendRequest(singleConnectionClient, "GET", uri, headers))
                  .getValueOrThrow();
            });

    optionsBuilder.setClientSpanErrorMapper(
        (uri, exception) -> {
          if (uri.toString().equals("https://192.0.2.1/")) {
            return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
          } else if (OS.WINDOWS.isCurrentOs() && uri.toString().equals("http://localhost:61/")) {
            return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
          } else if (uri.getPath().equals("/read-timeout")) {
            return new HttpClientReadTimeoutException(
                "Read timeout (PT2S) waiting on HTTP server at " + uri);
          }
          return exception;
        });
    optionsBuilder.setHttpAttributes(this::computeHttpAttributes);

    optionsBuilder.disableTestRedirects();
    // these tests will pass, but they don't really test anything since REQUEST is Void
    optionsBuilder.disableTestReusedRequest();

    optionsBuilder.spanEndsAfterBody();
  }

  protected Set<AttributeKey<?>> computeHttpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
    attributes.remove(NETWORK_PROTOCOL_VERSION);
    return attributes;
  }
}
