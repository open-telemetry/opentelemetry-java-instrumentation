/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

enum OpenFeignRpcAttributesGetter implements RpcAttributesGetter<ExecuteAndDecodeRequest> {
  INSTANCE;

  @Override
  public String system(ExecuteAndDecodeRequest request) {
    return "openfeign";
  }

  @Override
  public String service(ExecuteAndDecodeRequest request) {
    return request.getTarget().name();
  }

  @Nullable
  @Override
  public String method(ExecuteAndDecodeRequest request) {
    return null;
  }
}
