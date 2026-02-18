/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.client;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;

public class VirtualFields {
  private VirtualFields() {}

  public static final VirtualField<TAsyncMethodCall<?>, AsyncMethodCallback<?>>
      ASYNC_METHOD_CALLBACK = VirtualField.find(TAsyncMethodCall.class, AsyncMethodCallback.class);
}
