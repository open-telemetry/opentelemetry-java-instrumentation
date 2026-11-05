/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import javax.annotation.Nullable;

final class SofaRpcResponseUtil {

  static boolean isError(@Nullable SofaResponse response) {
    return response != null
        && (response.isError()
            || "true".equals(response.getResponseProp(RemotingConstants.HEAD_RESPONSE_ERROR)));
  }

  private SofaRpcResponseUtil() {}
}
