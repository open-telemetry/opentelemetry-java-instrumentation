/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * This class is an instrumentation module for ActiveJ HTTP server connections. It integrates with
 * OpenTelemetry to provide distributed tracing capabilities for applications using the ActiveJ HTTP
 * server.
 *
 * <p>The module is annotated with {@code @AutoService(InstrumentationModule.class)}, which
 * automatically registers it as a service provider for the OpenTelemetry instrumentation framework.
 * This allows the module to be discovered and loaded dynamically during runtime.
 *
 * @author Krishna Chaitanya Surapaneni
 */
@AutoService(InstrumentationModule.class)
public class ActiveJHttpServerConnectionInstrumentationModule extends InstrumentationModule {

  /**
   * Constructs the instrumentation module with the specified instrumentation names. These names are
   * used to identify the instrumentation in the OpenTelemetry framework.
   *
   * <p>In this case, the module is identified by the names "activej-http" and
   * "activej-http-server".
   */
  public ActiveJHttpServerConnectionInstrumentationModule() {
    super("activej-http", "activej-http-server");
  }

  /**
   * Returns a list of type instrumentation's provided by this module. Each type instrumentation
   * applies advice to specific methods or classes to capture trace context and propagate it through
   * HTTP requests and responses.
   *
   * @return A list containing the {@code ActiveJHttpServerConnectionInstrumentation} instance.
   */
  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActiveJHttpServerConnectionInstrumentation());
  }

  /**
   * Registers helper resources required for the instrumentation. Helper resources are typically
   * utility classes or configurations that support the instrumentation logic.
   *
   * <p>In this case, the {@code ActiveJHttpServerHelper} class is registered as a helper resource.
   * This class provides utilities for creating HTTP responses with trace context.
   *
   * @param helperResourceBuilder The builder used to register helper resources.
   */
  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register(ActiveJHttpServerHelper.class.getName());
  }
}
