/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AzureSdkInstrumentationModule extends InstrumentationModule {
  public AzureSdkInstrumentationModule() {
    super("azure-core", "azure-core-1.36");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register(
        "META-INF/services/com.azure.core.util.tracing.TracerProvider",
        "azure-core-1.36/META-INF/services/com.azure.core.util.tracing.TracerProvider");
    // some azure sdks (e.g. EventHubs) are still looking up Tracer via service loader
    // and not yet using the new TracerProvider
    helperResourceBuilder.register(
        "META-INF/services/com.azure.core.util.tracing.Tracer",
        "azure-core-1.36/META-INF/services/com.azure.core.util.tracing.Tracer");
  }

  @Override
  public boolean isIndyModule() {
    return false;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // this class was introduced in azure-core 1.36
    return hasClassesNamed("com.azure.core.util.tracing.TracerProvider")
        .and(not(hasClassesNamed("com.azure.core.tracing.opentelemetry.OpenTelemetryTracer")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new EmptyTypeInstrumentation(), new AzureHttpClientInstrumentation());
  }

  public static class EmptyTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return namedOneOf(
          "com.azure.core.util.tracing.TracerProvider", "com.azure.core.util.tracing.Tracer");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      // Nothing to instrument, no methods to match
    }
  }
}
