/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher.internal;

/**
 * Marker interface for delegating matchers that match based on the type hierarchy.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface DelegatingSuperTypeMatcher extends DelegatingMatcher {}
