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
public interface RpcAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the stable semconv system name for the RPC framework (e.g. {@code "grpc"}, {@code
   * "java_rmi"}, {@code "dotnet_wcf"}).
   *
   * @see <a
   *     href="https://opentelemetry.io/docs/specs/semconv/attributes-registry/rpc/">rpc.system.name
   *     spec</a>
   */
  @Nullable
  default String getRpcSystemName(REQUEST request) {
    return null;
  }

  /** @deprecated Use {@link #getRpcSystemName(REQUEST)}. To be removed in 3.0. */
  @Deprecated
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
   * Returns a description of a class of error the operation ended with.
   *
   * <p>This method should return {@code null} if there was no error.
   *
   * <p>If this method is not implemented, or if it returns {@code null}, the exception class name
   * will be used as error type.
   *
   * <p>The cardinality of the error type should be low. The instrumentations implementing this
   * method are recommended to document the custom values they support.
   *
   * <p>Examples: {@code OK}, {@code CANCELLED}, {@code UNKNOWN}, {@code -32602}
   */
  @Nullable
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }

}
