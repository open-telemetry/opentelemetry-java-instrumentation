/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import ratpack.exec.Promise;
import ratpack.http.client.HttpClient;
import ratpack.http.client.ReceivedResponse;

public abstract class AbstractRatpackForkedHttpClientTest extends AbstractRatpackHttpClientTest {
  @Override
  protected final Promise<Integer> internalSendRequest(
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

    return resp.fork().map(ReceivedResponse::getStatusCode);
  }
}
