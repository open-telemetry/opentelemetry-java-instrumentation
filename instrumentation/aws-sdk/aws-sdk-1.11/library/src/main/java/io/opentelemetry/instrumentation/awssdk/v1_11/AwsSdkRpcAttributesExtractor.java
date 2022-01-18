/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;

class AwsSdkRpcAttributesExtractor extends RpcAttributesExtractor<Request<?>, Response<?>> {

  private static final ClassValue<String> OPERATION_NAME =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          String ret = type.getSimpleName();
          ret = ret.substring(0, ret.length() - 7); // remove 'Request'
          return ret;
        }
      };

  @Override
  protected String system(Request<?> request) {
    return "aws-api";
  }

  @Override
  protected String service(Request<?> request) {
    return request.getServiceName();
  }

  @Override
  protected String method(Request<?> request) {
    return OPERATION_NAME.get(request.getOriginalRequest().getClass());
  }
}
