/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.context.ContextKey;

class MethodRequestKey {
  static final ContextKey<MethodRequest> KEY =
      ContextKey.named("opentelemetry-annotations-method-request-key");

  private MethodRequestKey() {}
}
