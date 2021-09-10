/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import org.apache.dubbo.rpc.Result;

final class DubboRpcAttributesExtractor extends RpcAttributesExtractor<DubboRequest, Result> {
  @Override
  protected String system(DubboRequest request) {
    return "dubbo";
  }

  @Override
  protected String service(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  protected String method(DubboRequest request) {
    return request.invocation().getMethodName();
  }
}
