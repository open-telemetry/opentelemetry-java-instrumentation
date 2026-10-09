/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

@AutoValue
public abstract class RequestAndContext {

  @Nullable private RequestAndContext previous;

  public static RequestAndContext create(HbaseRequest request, Scope scope, Context context) {
    return new AutoValue_RequestAndContext(request, scope, context);
  }

  public abstract HbaseRequest getRequest();

  public abstract Scope getScope();

  public abstract Context getContext();

  public void setPrevious(@Nullable RequestAndContext previous) {
    this.previous = previous;
  }

  @Nullable
  public RequestAndContext getPrevious() {
    return previous;
  }
}
