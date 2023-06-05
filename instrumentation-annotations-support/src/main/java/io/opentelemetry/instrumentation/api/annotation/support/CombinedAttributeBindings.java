/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.common.AttributesBuilder;

/** AttributeBindings implementation that is able to chain multiple AttributeBindings. */
final class CombinedAttributeBindings implements AttributeBindings {
  private final AttributeBindings parent;
  private final int index;
  private final AttributeBinding binding;

  public CombinedAttributeBindings(AttributeBindings parent, int index, AttributeBinding binding) {
    this.parent = parent;
    this.index = index;
    this.binding = binding;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void apply(AttributesBuilder target, Object[] args) {
    parent.apply(target, args);
    if (args != null && args.length > index) {
      Object arg = args[index];
      if (arg != null) {
        binding.apply(target, arg);
      }
    }
  }
}
