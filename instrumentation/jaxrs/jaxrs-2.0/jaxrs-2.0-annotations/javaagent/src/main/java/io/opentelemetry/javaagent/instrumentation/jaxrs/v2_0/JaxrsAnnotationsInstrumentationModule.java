/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxrsAnnotationsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public JaxrsAnnotationsInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "jaxrs-annotations", "jaxrs-2.0-annotations");
  }

  // require jax-rs 2
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.ws.rs.core.Configurable");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ContainerRequestFilterInstrumentation(),
        new DefaultRequestContextInstrumentation(),
        new JaxrsAnnotationsInstrumentation(),
        new JaxrsAsyncResponseInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // This instrumentation produces controller telemetry and sets http route. Http route is set by
    // this instrumentation only when it was not already set by a jax-rs framework instrumentation.
    // This instrumentation uses complex type matcher, disabling it can improve startup performance.
    return super.defaultEnabled(config) && ExperimentalConfig.get().controllerTelemetryEnabled();
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
