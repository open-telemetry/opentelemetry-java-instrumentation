/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.Nullable;

class KubernetesExperimentalAttributesExtractor
    extends AttributesExtractor<Request, ApiResponse<?>> {
  @Override
  protected void onStart(AttributesBuilder attributes, Request request) {
    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
    attributes
        .put("kubernetes-client.namespace", digest.getResourceMeta().getNamespace())
        .put("kubernetes-client.name", digest.getResourceMeta().getName());
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes, Request request, @Nullable ApiResponse<?> apiResponse) {}
}
