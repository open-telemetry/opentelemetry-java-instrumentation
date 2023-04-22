/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import okhttp3.Request;

class KubernetesNetAttributesGetter implements NetClientAttributesGetter<Request, ApiResponse<?>> {

  @Override
  public String getPeerName(Request request) {
    return request.url().host();
  }

  @Override
  public Integer getPeerPort(Request request) {
    return request.url().port();
  }
}
