/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

final class McpHttpRequestCustomizer implements McpAsyncHttpClientRequestCustomizer {
  private final McpAsyncHttpClientRequestCustomizer delegate;

  @Nullable
  static McpAsyncHttpClientRequestCustomizer wrap(
      @Nullable McpAsyncHttpClientRequestCustomizer customizer) {
    if (customizer == null
        || customizer == McpAsyncHttpClientRequestCustomizer.NOOP
        || customizer instanceof McpHttpRequestCustomizer) {
      return customizer;
    }
    return new McpHttpRequestCustomizer(customizer);
  }

  static void wrap(List<McpAsyncHttpClientRequestCustomizer> customizers) {
    for (int i = 0; i < customizers.size(); i++) {
      customizers.set(i, wrap(customizers.get(i)));
    }
  }

  private McpHttpRequestCustomizer(McpAsyncHttpClientRequestCustomizer delegate) {
    this.delegate = delegate;
  }

  @Override
  public Publisher<HttpRequest.Builder> customize(
      HttpRequest.Builder builder,
      String method,
      URI endpoint,
      @Nullable String body,
      McpTransportContext transportContext) {
    Context outgoingContext = Context.current();
    Context callerContext = McpToolCallState.getCallerContext(outgoingContext);
    if (callerContext == null) {
      return delegate.customize(builder, method, endpoint, body, transportContext);
    }
    try (Scope ignored = callerContext.makeCurrent()) {
      return new McpContextMono<>(
          Mono.from(delegate.customize(builder, method, endpoint, body, transportContext)),
          callerContext,
          outgoingContext);
    }
  }
}
