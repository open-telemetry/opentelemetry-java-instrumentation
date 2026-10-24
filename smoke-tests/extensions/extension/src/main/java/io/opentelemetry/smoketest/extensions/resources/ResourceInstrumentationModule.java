/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.resources;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ResourceInstrumentationModule extends InstrumentationModule {
  public ResourceInstrumentationModule() {
    super("smoke-test-extension-resources");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder builder) {
    builder.register("otel-smoke/injected-resource.txt", "resources/injected-resource.txt");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(
        new TypeInstrumentation() {
          @Override
          public ElementMatcher<TypeDescription> typeMatcher() {
            return named("io.opentelemetry.smoketest.springboot13.ResourceApplication");
          }

          @Override
          public void transform(TypeTransformer transformer) {
            // Matching the application class is enough to inject the helper resource.
          }
        });
  }
}
