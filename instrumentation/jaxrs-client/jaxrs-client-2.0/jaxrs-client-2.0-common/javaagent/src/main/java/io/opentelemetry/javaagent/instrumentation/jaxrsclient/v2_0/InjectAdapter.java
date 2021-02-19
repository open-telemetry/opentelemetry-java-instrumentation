/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.ws.rs.client.ClientRequestContext;

public final class InjectAdapter implements TextMapSetter<ClientRequestContext> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(ClientRequestContext carrier, String key, String value) {
    // Don't allow duplicates.
    carrier.getHeaders().putSingle(key, value);
  }
}
