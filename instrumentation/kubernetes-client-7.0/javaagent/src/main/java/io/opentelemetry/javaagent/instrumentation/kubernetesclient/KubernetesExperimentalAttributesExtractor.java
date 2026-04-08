/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import okhttp3.Request;

class KubernetesExperimentalAttributesExtractor
    implements AttributesExtractor<Request, ApiResponse<?>> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {
    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
    KubernetesResource resourceMeta = digest.getResourceMeta();
    if (resourceMeta == null) {
      return;
    }
    attributes
        .put("kubernetes-client.namespace", resourceMeta.getNamespace())
        .put("kubernetes-client.name", resourceMeta.getName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request request,
      @Nullable ApiResponse<?> apiResponse,
      @Nullable Throwable error) {}
}
