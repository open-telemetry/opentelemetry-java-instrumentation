/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxrsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public JaxrsInstrumentationModule() {
    super("jaxrs", "jaxrs-1.0");
  }

  // this is required to make sure instrumentation won't apply to jax-rs 2
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("javax.ws.rs.container.AsyncResponse"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JaxrsAnnotationsInstrumentation());
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
