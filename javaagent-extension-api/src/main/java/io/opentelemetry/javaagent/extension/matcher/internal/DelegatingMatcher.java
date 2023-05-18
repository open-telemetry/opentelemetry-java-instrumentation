/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher.internal;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * Interface for extracting the matcher that the given matcher delegates to.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface DelegatingMatcher {

  /** Returns the matcher that the current matcher delegates to. */
  ElementMatcher<?> getDelegate();
}
