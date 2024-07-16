/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import javax.annotation.Nullable;

final class RpcMessageBodySizeUtil {

  @Nullable
  static Long getRpcClientRequestBodySize(Attributes... attributesList) {
    return getAttribute(RpcCommonAttributesExtractor.RPC_CLIENT_REQUEST_BODY_SIZE, attributesList);
  }

  @Nullable
  static Long getRpcClientResponseBodySize(Attributes... attributesList) {
    return getAttribute(RpcCommonAttributesExtractor.RPC_CLIENT_RESPONSE_BODY_SIZE, attributesList);
  }

  @Nullable
  static Long getRpcServerRequestBodySize(Attributes... attributesList) {
    return getAttribute(RpcCommonAttributesExtractor.RPC_SERVER_REQUEST_BODY_SIZE, attributesList);
  }

  @Nullable
  static Long getRpcServerResponseBodySize(Attributes... attributesList) {
    return getAttribute(RpcCommonAttributesExtractor.RPC_SERVER_RESPONSE_BODY_SIZE, attributesList);
  }

  @Nullable
  private static <T> T getAttribute(AttributeKey<T> key, Attributes... attributesList) {
    for (Attributes attributes : attributesList) {
      T value = attributes.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private RpcMessageBodySizeUtil() {}
}
