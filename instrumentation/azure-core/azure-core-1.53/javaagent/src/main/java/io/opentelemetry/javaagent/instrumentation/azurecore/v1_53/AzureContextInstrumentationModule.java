/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_53;

import static io.opentelemetry.javaagent.instrumentation.azurecore.v1_53.AzureSdkInstrumentationModule.azureCoreClassLoaderMatcher;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Bridging an explicitly supplied parent context lives in its own module because it references the
 * application's (unshaded) {@code io.opentelemetry.context} classes. Muzzle therefore requires the
 * OpenTelemetry API to be present in the application class loader, which is not the case for
 * applications that use the Azure SDK without using the OpenTelemetry API themselves. Keeping this
 * in a separate module ensures that such applications still get the rest of the Azure SDK
 * instrumentation.
 */
@AutoService(InstrumentationModule.class)
public class AzureContextInstrumentationModule extends InstrumentationModule {

  public AzureContextInstrumentationModule() {
    super("azure-core", "azure-core-1.53", "azure-core-1.53-context");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return azureCoreClassLoaderMatcher();
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AzureContextInstrumentation());
  }
}
