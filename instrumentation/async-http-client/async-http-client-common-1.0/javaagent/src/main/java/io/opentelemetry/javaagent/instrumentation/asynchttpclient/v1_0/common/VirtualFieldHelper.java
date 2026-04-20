/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_0.common;

import com.ning.http.client.AsyncHandler;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class VirtualFieldHelper {
  public static final VirtualField<AsyncHandler<?>, AsyncHandlerData> ASYNC_HANDLER_DATA =
      VirtualField.find(AsyncHandler.class, AsyncHandlerData.class);

  private VirtualFieldHelper() {}
}
