/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import io.opentelemetry.context.propagation.TextMapSetter;

final class ClientRequestContextSetter implements TextMapSetter<ClientRequestContext> {

  static final ClientRequestContextSetter INSTANCE = new ClientRequestContextSetter();

  @Override
  public void set(ClientRequestContext carrier, String key, String value) {
    if (carrier != null) {
      carrier.addAdditionalRequestHeader(key, value);
    }
  }
}
