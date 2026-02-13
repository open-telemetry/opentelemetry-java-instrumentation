/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

enum AwsSdkRpcAttributesGetter implements RpcAttributesGetter<Request<?>, Response<?>> {
  INSTANCE;

  @Override
  public String getSystem(Request<?> request) {
    return "aws-api";
  }

  @Override
  public String getService(Request<?> request) {
    return request.getServiceName();
  }
}
