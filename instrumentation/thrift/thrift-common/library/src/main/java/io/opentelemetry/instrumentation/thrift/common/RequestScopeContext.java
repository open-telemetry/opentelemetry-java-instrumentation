/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

@AutoValue
public abstract class RequestScopeContext {

  public static RequestScopeContext create(
      ThriftRequest request, @Nullable Scope scope, Context context) {
    return new AutoValue_RequestScopeContext(request, scope, context);
  }

  public abstract ThriftRequest getRequest();

  @Nullable
  public abstract Scope getScope();

  public abstract Context getContext();

  public void close() {
    if (getScope() != null) {
      getScope().close();
    }
  }
}
