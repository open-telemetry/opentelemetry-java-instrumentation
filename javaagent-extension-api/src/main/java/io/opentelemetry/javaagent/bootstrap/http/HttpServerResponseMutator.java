/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.http;

/** Provides the ability to mutate an instrumentation library specific response. */
public interface HttpServerResponseMutator<RESPONSE> {
  void appendHeader(RESPONSE response, String name, String value);
}
