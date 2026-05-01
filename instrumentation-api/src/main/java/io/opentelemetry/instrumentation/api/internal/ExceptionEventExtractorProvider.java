/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import javax.annotation.Nullable;

/**
 * Provides the default exception event extractor for a semantic convention.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface ExceptionEventExtractorProvider {

  @Nullable
  InternalExceptionEventExtractor<?> internalGetExceptionEventExtractor();
}
