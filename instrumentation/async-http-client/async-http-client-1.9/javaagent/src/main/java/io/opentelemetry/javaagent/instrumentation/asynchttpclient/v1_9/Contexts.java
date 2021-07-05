/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;

@AutoValue
public abstract class Contexts {

  public static Contexts create(Context parentContext, Context context) {
    return new AutoValue_Contexts(parentContext, context);
  }

  public abstract Context getParentContext();

  public abstract Context getContext();

  Contexts() {}
}
