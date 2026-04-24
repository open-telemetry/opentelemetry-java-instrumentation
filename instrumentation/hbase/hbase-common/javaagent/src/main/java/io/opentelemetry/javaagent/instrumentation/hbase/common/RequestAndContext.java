/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;

@AutoValue
public abstract class RequestAndContext {

  public static RequestAndContext create(HbaseRequest request, Context context) {
    return new AutoValue_RequestAndContext(request, context);
  }

  public abstract HbaseRequest getRequest();

  public abstract Context getContext();
}
