/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

public final class ResteasyInjectAdapter implements TextMapPropagator.Setter<ClientInvocation> {

  public static final ResteasyInjectAdapter SETTER = new ResteasyInjectAdapter();

  @Override
  public void set(ClientInvocation carrier, String key, String value) {
    // Don't allow duplicates.
    carrier.getHeaders().header(key, value);
  }
}
