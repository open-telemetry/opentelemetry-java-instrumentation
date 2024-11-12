/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.netty.channel.ChannelOption;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
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
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    optionsBuilder.setSingleConnectionFactory(
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

  @Override
  protected Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    if (uri.toString().contains("/success")) {
      // the single connection test does not report net.peer.* attributes; it only reports the
      // net.peer.sock.* attributes
      Set<AttributeKey<?>> attributes =
          new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
      attributes.remove(SERVER_ADDRESS);
      attributes.remove(SERVER_PORT);
      return attributes;
    }
    return super.getHttpAttributes(uri);
  }
}
