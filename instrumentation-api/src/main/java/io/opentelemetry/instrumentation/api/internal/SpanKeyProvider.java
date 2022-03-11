/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.stream.Stream;

/**
 * Returns {@link SpanKey}s associated with the {@link AttributesExtractor} that implements this
 * interface.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface SpanKeyProvider {

  Stream<SpanKey> internalGetSpanKeys();
}
