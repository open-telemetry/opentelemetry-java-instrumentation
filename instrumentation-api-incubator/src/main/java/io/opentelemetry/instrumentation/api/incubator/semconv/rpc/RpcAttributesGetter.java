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
   * <p>The default implementation concatenates service + "/" + method. Framework implementations
   * can override for efficiency if they already have the fully-qualified name available.
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
   * Returns whether the RPC method is a well-known method.
   *
   * <p>This is used to determine whether to set the {@code rpc.method} attribute to the actual
   * method name, or to set it to {@code "_OTHER"} and store the actual method name in {@code
   * rpc.method_original}.
   */
  default boolean isWellKnownMethod(REQUEST request) {
    return false;
  }
}
