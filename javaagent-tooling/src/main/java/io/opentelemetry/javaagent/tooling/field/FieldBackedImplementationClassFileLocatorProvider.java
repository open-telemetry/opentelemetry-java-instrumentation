/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.tooling.muzzle.ClassFileLocatorProvider;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import org.jetbrains.annotations.Nullable;

@AutoService(ClassFileLocatorProvider.class)
public final class FieldBackedImplementationClassFileLocatorProvider
    implements ClassFileLocatorProvider {

  @Nullable
  @Override
  public ClassFileLocator getClassFileLocator() {
    if (!FieldBackedImplementationConfiguration.fieldInjectionEnabled) {
      return null;
    }

    return new VirtualFieldInterfaceLocator();
  }

  private static class VirtualFieldInterfaceLocator implements ClassFileLocator {
    private static final ByteBuddy byteBuddy = new ByteBuddy();

    private static DynamicType.Unloaded<?> makeFieldAccessorInterface(String name) {
      // create trimmed down version of the interface
      return byteBuddy
          .makeInterface()
          .merge(SyntheticState.SYNTHETIC)
          .name(name)
          .implement(VirtualFieldAccessorMarker.class)
          .make();
    }

    @Override
    public Resolution locate(String name) {
      if (GeneratedVirtualFieldNames.isVirtualFieldInterfaceName(name)) {
        try (DynamicType.Unloaded<?> type = makeFieldAccessorInterface(name)) {
          return new Resolution.Explicit(type.getBytes());
        }
      }
      return new Resolution.Illegal(name);
    }

    @Override
    public void close() {}
  }
}
