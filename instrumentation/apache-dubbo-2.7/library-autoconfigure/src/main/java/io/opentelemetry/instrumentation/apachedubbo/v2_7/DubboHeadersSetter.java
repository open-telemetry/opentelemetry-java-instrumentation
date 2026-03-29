/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

final class DubboHeadersSetter implements TextMapSetter<DubboRequest> {

  @Override
  public void set(@Nullable DubboRequest request, String key, String value) {
    if (request == null) {
      return;
    }
    request.context().setAttachment(key, value);
    request.invocation().setAttachment(key, value);
  }
}
