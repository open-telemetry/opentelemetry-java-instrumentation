/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.jboss.resteasy.core.ResourceLocatorInvoker;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public final class ResteasySingletons {

  public static final VirtualField<ResourceLocatorInvoker, String> LOCATOR_NAME =
      VirtualField.find(ResourceLocatorInvoker.class, String.class);

  public static final VirtualField<ResourceMethodInvoker, String> INVOKER_NAME =
      VirtualField.find(ResourceMethodInvoker.class, String.class);

  private ResteasySingletons() {}
}
