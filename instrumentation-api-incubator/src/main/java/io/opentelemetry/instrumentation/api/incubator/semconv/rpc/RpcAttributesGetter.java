/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import javax.annotation.Nullable;

/**
 * An interface for getting RPC attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link RpcClientAttributesExtractor} or {@link
 * RpcServerAttributesExtractor} to obtain the various RPC attributes in a type-generic way.
 */
public interface RpcAttributesGetter<REQUEST> {

  @Nullable
  String getSystem(REQUEST request);

  @Nullable
  String getService(REQUEST request);

  /**
   * @deprecated Use {@link #getRpcMethod(REQUEST)} for stable semconv.
   */
  @Deprecated
  @Nullable
  String getMethod(REQUEST request);

  @Nullable
  default Long getRequestSize(REQUEST request) {
    return null;
  }

  @Nullable
  default Long getResponseSize(REQUEST request) {
    return null;
  }

  /**
   * Returns the fully-qualified RPC method name for stable semconv.
   *
   * @param request the request object
   * @return the fully-qualified RPC method name (e.g., "my.Service/Method"), or null if service or
   *     method is unavailable
   */
  @Nullable
  default String getRpcMethod(REQUEST request) {
    return null;
  }

  /**
   * Returns whether the RPC method is recognized as a predefined method by the RPC framework or
   * library.
   *
   * <p>Some RPC frameworks or libraries provide a fixed set of recognized methods for client stubs
   * and server implementations. Instrumentations for such frameworks MUST return {@code true} only
   * when the method is recognized by the framework or library.
   *
   * <p>When the method is not recognized (for example, when the server receives a request for a
   * method that is not predefined on the server), or when instrumentation is not able to reliably
   * detect if the method is predefined, this method MUST return {@code false}.
   *
   * <p>When this method returns {@code false}, the {@code rpc.method} attribute will be set to
   * {@code "_OTHER"} and the {@code rpc.method_original} attribute will be set to the original
   * method name.
   *
   * <p>Note: If the RPC instrumentation could end up converting valid RPC methods to {@code
   * "_OTHER"}, then it SHOULD provide a way to configure the list of recognized RPC methods.
   *
   * @param request the request object
   * @return {@code true} if the method is recognized as predefined by the framework, {@code false}
   *     otherwise
   */
  default boolean isPredefined(REQUEST request) {
    return false;
  }
}
