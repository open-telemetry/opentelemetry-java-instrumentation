/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.opentelemetry.api.common.AttributeKey;

final class GrpcHelper {

  static final AttributeKey<String> MESSAGE_TYPE = AttributeKey.stringKey("message.type");
  static final AttributeKey<Long> MESSAGE_ID = AttributeKey.longKey("message.id");
  static final AttributeKey<String> RPC_GRPC_AUTHORITY =
      AttributeKey.stringKey("rpc.grpc.authority");

  private GrpcHelper() {}
}
