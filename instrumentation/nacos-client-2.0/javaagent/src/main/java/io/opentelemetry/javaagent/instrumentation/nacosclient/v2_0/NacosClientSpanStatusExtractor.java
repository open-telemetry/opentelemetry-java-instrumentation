/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

class NacosClientSpanStatusExtractor implements SpanStatusExtractor<NacosClientRequest, Response> {

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      NacosClientRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {
    SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
    if (response != null && !response.isSuccess()) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }
}
