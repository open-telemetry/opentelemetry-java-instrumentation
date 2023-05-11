/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;

/** Singleton empty implementation of AttributeBindings. */
enum EmptyAttributeBindings implements AttributeBindings {
  INSTANCE;

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public void apply(AttributesBuilder target, Object[] args) {}
}
