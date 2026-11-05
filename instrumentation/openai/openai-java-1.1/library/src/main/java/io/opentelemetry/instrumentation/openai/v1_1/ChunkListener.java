/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import javax.annotation.Nullable;

/** Receives the chunks of a streamed response and ends its span. */
interface ChunkListener<T> {

  void onChunk(T chunk);

  void endSpan(@Nullable Throwable error);
}
