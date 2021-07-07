/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client;

import static io.opentelemetry.instrumentation.test.base.SingleConnection.REQUEST_ID_HEADER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.base.SingleConnection;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class VertxSingleConnection implements SingleConnection {

  private final HttpClient httpClient;
  private final String host;
  private final int port;

  public VertxSingleConnection(String host, int port) {
    this.host = host;
    this.port = port;
    HttpClientOptions clientOptions =
        new HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true).setPipelining(true);
    httpClient = Vertx.vertx(new VertxOptions()).createHttpClient(clientOptions);
  }

  @Override
  public int doRequest(String path, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER));
    /*
       String url;
       try {
         url = new URL("http", host, port, path).toString();
       } catch (MalformedURLException e) {
         throw new ExecutionException(e);
       }

    */
    RequestOptions requestOptions = new RequestOptions().setHost(host).setPort(port).setURI(path);
    headers.forEach(requestOptions::putHeader);
    Future<HttpClientRequest> request = httpClient.request(requestOptions);
    //    request.compose(req -> req.send()).result()
    /*
       Future<HttpClientRequest> request = httpClient.request(HttpMethod.GET, port, host, url);
       headers.forEach(request::putHeader);
    */
    /*
       CompletableFuture<HttpClientResponse> future = new CompletableFuture<>();
       request.handler(future::complete);

       request.end();
       HttpClientResponse response = future.get();
       String responseId = response.getHeader(REQUEST_ID_HEADER);
    */
    Context ctx = Context.current();
    HttpClientResponse response =
        request.compose(req -> send(ctx, req)).toCompletionStage().toCompletableFuture().get();
    //    HttpClientResponse response = request.compose(req ->
    // req.send()).toCompletionStage().toCompletableFuture().get();
    //    HttpClientResponse response = request.compose(req -> req.send()).result();
    String responseId = response.getHeader(REQUEST_ID_HEADER);
    if (!requestId.equals(responseId)) {
      throw new IllegalStateException(
          String.format("Received response with id %s, expected %s", responseId, requestId));
    }
    return response.statusCode();
  }

  private Future<HttpClientResponse> send(Context context, HttpClientRequest req) {
    Context ctx = Context.current();
    if (!ctx.equals(context)) {
      System.err.println("fail");
    }
    try (Scope ignore = context.makeCurrent()) {
      return req.send();
    }
  }
}
