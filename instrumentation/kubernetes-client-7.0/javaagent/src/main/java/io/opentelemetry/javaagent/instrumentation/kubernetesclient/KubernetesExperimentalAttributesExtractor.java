/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import okhttp3.Request;

class KubernetesExperimentalAttributesExtractor
    implements AttributesExtractor<Request, ApiResponse<?>> {
  @Override
  public void onStart(AttributesBuilder attributes, Request request) {
    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
    attributes
        .put("kubernetes-client.namespace", digest.getResourceMeta().getNamespace())
        .put("kubernetes-client.name", digest.getResourceMeta().getName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Request request,
      @Nullable ApiResponse<?> apiResponse,
      @Nullable Throwable error) {}
}
