/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;

final class NoopVirtualFieldImplementationInstaller implements VirtualFieldImplementationInstaller {

  static final NoopVirtualFieldImplementationInstaller INSTANCE =
      new NoopVirtualFieldImplementationInstaller();

  private NoopVirtualFieldImplementationInstaller() {}

  @Override
  @CanIgnoreReturnValue
  public Extendable rewriteVirtualFieldsCalls(Extendable builder) {
    return builder;
  }

  @Override
  @CanIgnoreReturnValue
  public Extendable injectHelperClasses(Extendable builder) {
    return builder;
  }

  @Override
  @CanIgnoreReturnValue
  public Extendable injectFields(Extendable builder) {
    return builder;
  }
}
