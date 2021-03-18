/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import org.checkerframework.checker.nullness.qual.Nullable;

final class DefaultStatusExtractor<REQUEST, RESPONSE>
    implements StatusExtractor<REQUEST, RESPONSE> {

  static final StatusExtractor<Object, Object> INSTANCE = new DefaultStatusExtractor<>();

  @Override
  public StatusCode extract(REQUEST request, RESPONSE response, @Nullable Throwable error) {
    if (error != null) {
      return StatusCode.ERROR;
    }
    return StatusCode.UNSET;
  }
}
