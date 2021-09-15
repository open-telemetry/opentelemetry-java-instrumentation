/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.opentelemetry.context.propagation.TextMapSetter;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Helper class to inject span context into request headers. */
// TODO(anuraaga): Figure out a way to avoid copying this from okhttp instrumentation.
final class RequestBuilderInjectAdapter implements TextMapSetter<Request.Builder> {

  static final RequestBuilderInjectAdapter SETTER = new RequestBuilderInjectAdapter();

  @Override
  public void set(Request.@Nullable Builder carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.header(key, value);
  }
}
