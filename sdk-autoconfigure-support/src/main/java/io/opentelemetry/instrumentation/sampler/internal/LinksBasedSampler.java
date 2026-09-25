/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

final class LinksBasedSampler implements Sampler {
  private final Sampler root;

  LinksBasedSampler(Sampler root) {
    this.root = root;
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    if (!parentLinks.isEmpty()) {
      for (LinkData linkData : parentLinks) {
        if (linkData.getSpanContext().isSampled()) {
          return SamplingResult.recordAndSample();
        }
      }
      return SamplingResult.drop();
    }

    return root.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
  }

  @Override
  public String getDescription() {
    return String.format("LinksBased{root:%s}", root.getDescription());
  }

  @Override
  public String toString() {
    return getDescription();
  }
}
