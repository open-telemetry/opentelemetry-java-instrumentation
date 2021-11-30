/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;
import javax.annotation.Nullable;

/** Extractor of the start and end times of request processing. */
public interface TimeExtractor<REQUEST, RESPONSE> {

  /** Returns the timestamp marking the start of the request processing. */
  Instant extractStartTime(REQUEST request);

  /** Returns the timestamp marking the end of the response processing. */
  Instant extractEndTime(REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error);
}
