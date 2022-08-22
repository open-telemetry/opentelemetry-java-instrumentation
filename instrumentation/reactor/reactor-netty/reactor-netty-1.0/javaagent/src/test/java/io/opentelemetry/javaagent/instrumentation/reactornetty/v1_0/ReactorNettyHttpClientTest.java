/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.channel.ChannelOption;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import reactor.netty.http.client.HttpClient;

class ReactorNettyHttpClientTest extends AbstractReactorNettyHttpClientTest {

  @Override
  protected HttpClient createHttpClient() {
    int connectionTimeoutMillis = (int) CONNECTION_TIMEOUT.toMillis();
    return HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis)
        .resolver(getAddressResolverGroup())
        .headers(headers -> headers.set(HttpHeaderNames.USER_AGENT, USER_AGENT));
  }

  @Override
  protected void configure(HttpClientTestOptions options) {
    super.configure(options);

    options.setSingleConnectionFactory(
        (host, port) -> {
          HttpClient httpClient =
              HttpClient.newConnection()
                  .host(host)
                  .port(port)
                  .headers(headers -> headers.set(HttpHeaderNames.USER_AGENT, USER_AGENT));

          return (path, headers) ->
              httpClient
                  .headers(h -> headers.forEach(h::add))
                  .get()
                  .uri(path)
                  .responseSingle(
                      (resp, content) -> {
                        // Make sure to consume content since that's when we close the span.
                        return content.map(unused -> resp);
                      })
                  .block()
                  .status()
                  .code();
        });
  }
}
