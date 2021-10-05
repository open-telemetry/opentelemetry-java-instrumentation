/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilderImpl;

public final class VirtualFieldImplementationInstallerFactory {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(VirtualFieldImplementationInstallerFactory.class);

  public VirtualFieldImplementationInstallerFactory() {
    RuntimeVirtualFieldSupplier.set(new RuntimeFieldBasedImplementationSupplier());
  }

  public VirtualFieldImplementationInstaller create(InstrumentationModule instrumentationModule) {
    VirtualFieldMappingsBuilderImpl builder = new VirtualFieldMappingsBuilderImpl();
    if (instrumentationModule instanceof InstrumentationModuleMuzzle) {
      ((InstrumentationModuleMuzzle) instrumentationModule).registerMuzzleVirtualFields(builder);
    } else {
      logger.debug(
          "Found InstrumentationModule which does not implement InstrumentationModuleMuzzle: {}",
          instrumentationModule);
    }
    VirtualFieldMappings mappings = builder.build();

    return mappings.isEmpty()
        ? NoopVirtualFieldImplementationInstaller.INSTANCE
        : new FieldBackedImplementationInstaller(instrumentationModule.getClass(), mappings);
  }
}
