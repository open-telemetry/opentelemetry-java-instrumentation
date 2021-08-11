/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;

@AutoValue
public abstract class AsyncResponseData {

  public static AsyncResponseData create(Context context, HandlerData handlerData) {
    return new AutoValue_AsyncResponseData(context, handlerData);
  }

  public abstract Context getContext();

  public abstract HandlerData getHandlerData();
}
