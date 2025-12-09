/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

public enum ThriftRpcAttributesGetter implements RpcAttributesGetter<ThriftRequest> {
  INSTANCE;

  @Override
  public String getSystem(ThriftRequest request) {
    return "apache_thrift";
  }

  @Override
  @Nullable
  public String getService(ThriftRequest request) {
    return request.getServiceName();
  }

  @Override
  @Nullable
  public String getMethod(ThriftRequest request) {
    return request.getMethodName();
  }
}
