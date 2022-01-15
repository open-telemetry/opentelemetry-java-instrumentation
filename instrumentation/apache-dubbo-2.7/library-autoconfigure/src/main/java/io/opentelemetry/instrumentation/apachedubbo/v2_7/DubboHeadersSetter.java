/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapSetter;

enum DubboHeadersSetter implements TextMapSetter<DubboRequest> {
  INSTANCE;

  @Override
  public void set(DubboRequest request, String key, String value) {
    request.context().setAttachment(key, value);
  }
}
