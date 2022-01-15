/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum ClientRequestContextSetter implements TextMapSetter<ClientRequestContext> {
  INSTANCE;

  @Override
  public void set(@Nullable ClientRequestContext carrier, String key, String value) {
    if (carrier != null) {
      carrier.setAdditionalRequestHeader(key, value);
    }
  }
}
