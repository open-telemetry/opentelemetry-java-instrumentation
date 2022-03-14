/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesGetter;

enum AwsSdkRpcAttributesGetter implements RpcClientAttributesGetter<Request<?>> {
  INSTANCE;

  private static final ClassValue<String> OPERATION_NAME =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          String ret = type.getSimpleName();
          if (!ret.endsWith("Request")) {
            // Best effort check one parent to support implicit subclasses
            ret = type.getSuperclass().getSimpleName();
          }
          if (ret.endsWith("Request")) {
            ret = ret.substring(0, ret.length() - 7); // remove 'Request'
          }
          return ret;
        }
      };

  @Override
  public String system(Request<?> request) {
    return "aws-api";
  }

  @Override
  public String service(Request<?> request) {
    return request.getServiceName();
  }

  @Override
  public String method(Request<?> request) {
    return OPERATION_NAME.get(request.getOriginalRequest().getClass());
  }
}
