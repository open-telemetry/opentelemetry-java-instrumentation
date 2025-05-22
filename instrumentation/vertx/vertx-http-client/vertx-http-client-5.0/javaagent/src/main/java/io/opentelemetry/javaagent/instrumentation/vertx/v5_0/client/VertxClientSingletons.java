/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.client.VertxClientInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.net.HostAndPort;

public final class VertxClientSingletons {

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER =
      VertxClientInstrumenterFactory.create(
          "io.opentelemetry.vertx-http-client-5.0", new Vertx5HttpAttributesGetter());

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private static final VirtualField<HttpClientRequest, HostAndPort> authorityField =
      VirtualField.find(HttpClientRequest.class, HostAndPort.class);

  public static void setAuthority(HttpClientRequest request, HostAndPort authority) {
    authorityField.set(request, authority);
  }

  public static HostAndPort getAuthority(HttpClientRequest request) {
    return authorityField.get(request);
  }

  private static final VirtualField<ResourceManager<?, ?>, VertxInternal> vertxField =
      VirtualField.find(ResourceManager.class, VertxInternal.class);

  public static void setVertx(ResourceManager<?, ?> resourceManager, VertxInternal vertx) {
    vertxField.set(resourceManager, vertx);
  }

  public static VertxInternal getVertx(ResourceManager<?, ?> resourceManager) {
    return vertxField.get(resourceManager);
  }

  public static <T> Future<T> wrapFuture(ContextInternal vertxContext, Future<T> future) {
    Context context = Context.current();
    Promise<T> promise = vertxContext.promise();
    future.onComplete(
        result -> {
          try (Scope ignore = context.makeCurrent()) {
            if (result.failed()) {
              promise.fail(result.cause());
            } else {
              promise.complete(result.result());
            }
          }
        });
    return promise.future();
  }

  private VertxClientSingletons() {}
}
