/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.impl;

import com.alipay.sofa.rpc.core.exception.SofaRpcRuntimeException;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.ErrorService;

public class ErrorServiceImpl implements ErrorService {
  @Override
  public String throwException() {
    throw new SofaRpcRuntimeException("RPC error");
  }

  @Override
  public String throwBusinessException() {
    throw new IllegalStateException("Business error");
  }

  @Override
  public String timeout() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return "timeout";
  }
}
