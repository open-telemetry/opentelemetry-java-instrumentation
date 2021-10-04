/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;

public class NoopVirtualFieldImplementationInstaller
    implements VirtualFieldImplementationInstaller {

  public static final NoopVirtualFieldImplementationInstaller INSTANCE =
      new NoopVirtualFieldImplementationInstaller();

  private NoopVirtualFieldImplementationInstaller() {}

  @Override
  public Extendable rewriteVirtualFieldsCalls(Extendable builder) {
    return builder;
  }

  @Override
  public Extendable installFields(Extendable builder) {
    return builder;
  }
}
