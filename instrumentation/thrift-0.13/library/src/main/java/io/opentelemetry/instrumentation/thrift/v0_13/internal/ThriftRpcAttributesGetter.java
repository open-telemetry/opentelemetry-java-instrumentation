/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import javax.annotation.Nullable;
import org.apache.thrift.transport.TTransportException;

final class ThriftRpcAttributesGetter
    implements RpcAttributesGetter<ThriftRequest, ThriftResponse> {

  @Override
  public String getSystem(ThriftRequest request) {
    return "apache_thrift";
  }

  @Nullable
  @Override
  public String getService(ThriftRequest request) {
    return request.getServiceName();
  }

  @Nullable
  @Override
  public String getMethod(ThriftRequest request) {
    return request.getMethodName();
  }

  @Override
  @Nullable
  public String getRpcMethod(ThriftRequest request) {
    String service = getService(request);
    String method = getMethod(request);
    if (service != null && method != null) {
      return service + "/" + method;
    }
    return method;
  }

  @Override
  @Nullable
  public String getErrorType(
      ThriftRequest request, @Nullable ThriftResponse response, @Nullable Throwable error) {
    if (error instanceof TTransportException) {
      int errorCode = ((TTransportException) error).getType();
      switch (((TTransportException) error).getType()) {
        case 0:
          return "UNKNOWN";
        case 1:
          return "NOT_OPEN";
        case 2:
          return "ALREADY_OPEN";
        case 3:
          return "TIMED_OUT";
        case 4:
          return "END_OF_FILE";
        case 5:
          return "CORRUPTED_DATA";
        default:
          return String.valueOf(errorCode);
      }
    }
    return null;
  }
}
