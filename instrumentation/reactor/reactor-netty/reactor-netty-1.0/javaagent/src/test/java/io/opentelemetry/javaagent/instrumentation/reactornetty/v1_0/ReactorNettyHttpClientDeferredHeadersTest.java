/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpMethod;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import java.net.URI;
import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

class ReactorNettyHttpClientDeferredHeadersTest extends AbstractReactorNettyHttpClientTest {

  @Override
  protected HttpClient createHttpClient() {
    int connectionTimeoutMillis = (int) CONNECTION_TIMEOUT.toMillis();
    return HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis)
        .resolver(getAddressResolverGroup())
        .headers(headers -> headers.set(HttpHeaderNames.USER_AGENT, USER_AGENT));
  }

  @Override
  public HttpClient.ResponseReceiver<?> buildRequest(
      String method, URI uri, Map<String, String> headers) {
    HttpClient client =
        createHttpClient()
            .followRedirect(true)
            .headersWhen(
                h -> {
                  headers.forEach(h::add);
                  return Mono.just(h);
                })
            .baseUrl(resolveAddress("").toString());
    if (uri.toString().contains("/read-timeout")) {
      client = client.responseTimeout(READ_TIMEOUT);
    }
    return client.request(HttpMethod.valueOf(method)).uri(uri.toString());
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    // these scenarios don't work because deferred config does not apply the doOnRequestError()
    // callback
    optionsBuilder.disableTestReadTimeout();
    optionsBuilder.disableTestConnectionFailure();
    optionsBuilder.disableTestRemoteConnection();
  }
}
