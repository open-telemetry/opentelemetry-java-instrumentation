/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapGetter;

enum DubboHeadersGetter implements TextMapGetter<DubboRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(DubboRequest request) {
    return request.invocation().getAttachments().keySet();
  }

  @Override
  public String get(DubboRequest request, String key) {
    return request.invocation().getAttachment(key);
  }
}
