/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import io.opentelemetry.context.propagation.TextMapPropagator;

public final class InjectAdapter implements TextMapPropagator.Setter<ClientRequest> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(ClientRequest carrier, String key, String value) {
    carrier.getHeaders().putSingle(key, value);
  }
}
