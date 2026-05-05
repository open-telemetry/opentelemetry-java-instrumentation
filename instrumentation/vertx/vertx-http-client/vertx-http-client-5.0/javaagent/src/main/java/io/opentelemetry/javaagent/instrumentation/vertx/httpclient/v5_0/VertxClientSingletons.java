/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0.Contexts;
import io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0.VertxClientInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.net.HostAndPort;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

public class VertxClientSingletons {

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter =
      VertxClientInstrumenterFactory.create(
          "io.opentelemetry.vertx-http-client-5.0", new Vertx5HttpAttributesGetter());

  private static final VirtualField<HttpClientRequest, HostAndPort> AUTHORITY_FIELD =
      VirtualField.find(HttpClientRequest.class, HostAndPort.class);

  public static final VirtualField<HttpClientRequest, Contexts> CONTEXTS =
      VirtualField.find(HttpClientRequest.class, Contexts.class);

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return instrumenter;
  }

  public static void setAuthority(HttpClientRequest request, @Nullable HostAndPort authority) {
    AUTHORITY_FIELD.set(request, authority);
  }

  @Nullable
  public static HostAndPort getAuthority(HttpClientRequest request) {
    return AUTHORITY_FIELD.get(request);
  }

  public static <T> Future<T> wrapFuture(Future<T> future) {
    Context context = Context.current();
    CompletableFuture<T> result = new CompletableFuture<>();
    future
        .toCompletionStage()
        .whenComplete(
            (value, throwable) -> {
              try (Scope ignore = context.makeCurrent()) {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(value);
                }
              }
            });
    return Future.fromCompletionStage(result);
  }

  private VertxClientSingletons() {}
}
