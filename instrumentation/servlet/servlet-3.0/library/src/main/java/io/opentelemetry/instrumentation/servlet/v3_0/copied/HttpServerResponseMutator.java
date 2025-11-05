/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

/** Provides the ability to mutate an instrumentation library specific response. */
public interface HttpServerResponseMutator<RESPONSE> {
  void appendHeader(RESPONSE response, String name, String value);
}
