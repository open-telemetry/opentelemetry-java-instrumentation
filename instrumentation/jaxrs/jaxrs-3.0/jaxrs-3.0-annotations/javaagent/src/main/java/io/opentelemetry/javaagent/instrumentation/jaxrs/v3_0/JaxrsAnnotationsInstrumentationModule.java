/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxrsAnnotationsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public JaxrsAnnotationsInstrumentationModule() {
    super("jaxrs", "jaxrs-3.0", "jaxrs-annotations", "jaxrs-3.0-annotations");
  }

  // require jax-rs 3
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("jakarta.ws.rs.Path");
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
  public boolean defaultEnabled() {
    // This instrumentation produces controller telemetry and sets http route. Http route is set by
    // this instrumentation only when it was not already set by a jax-rs framework instrumentation.
    // This instrumentation uses complex type matcher, disabling it can improve startup performance.
    return superDefaultEnabled()
        && DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "common",
                "controller_telemetry/development",
                "enabled")
            .orElse(false);
  }

  // This method can be removed and super.defaultEnabled() can be used instead once the deprecated
  // InstrumentationModule.defaultEnabled(ConfigProperties) is removed, at which point
  // InstrumentationModule.defaultEnabled() will no longer need to throw an exception.
  private static boolean superDefaultEnabled() {
    return DeclarativeConfigUtil.getBoolean(
            GlobalOpenTelemetry.get(), "java", "common", "default_enabled")
        .orElse(true);
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
