/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

/**
 * An interface for getting the {@code http.route} attribute.
 *
 * @deprecated This class is deprecated and will be removed in the 2.0 release. Use {@link
 *     HttpServerRouteBiGetter} instead.
 */
@Deprecated
@FunctionalInterface
public interface HttpRouteBiGetter<T, U> extends HttpServerRouteBiGetter<T, U> {}
