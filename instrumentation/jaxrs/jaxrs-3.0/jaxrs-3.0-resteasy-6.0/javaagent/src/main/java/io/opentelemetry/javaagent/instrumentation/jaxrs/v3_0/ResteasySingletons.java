/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;
import org.jboss.resteasy.core.ResourceLocatorInvoker;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public final class ResteasySingletons {

  private static final Instrumenter<HandlerData, Void> INSTANCE =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxrs-3.0-resteasy-6.0");

  public static final VirtualField<ResourceMethodInvoker, String> INVOKER_NAME =
      VirtualField.find(ResourceMethodInvoker.class, String.class);

  public static final VirtualField<ResourceLocatorInvoker, String> LOCATOR_NAME =
      VirtualField.find(ResourceLocatorInvoker.class, String.class);

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTANCE;
  }

  private ResteasySingletons() {}
}
