/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public final class CouchbaseRequestInfoHolder implements ImplicitContextKeyed {

  private static final ContextKey<CouchbaseRequestInfoHolder> KEY =
      named("opentelemetry-couchbase-request-key");

  private final CouchbaseRequestInfo couchbaseRequest;

  private CouchbaseRequestInfoHolder(CouchbaseRequestInfo couchbaseRequest) {
    this.couchbaseRequest = couchbaseRequest;
  }

  public static Context init(Context context, CouchbaseRequestInfo couchbaseRequest) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new CouchbaseRequestInfoHolder(couchbaseRequest));
  }

  @Nullable
  public static CouchbaseRequestInfo get(Context context) {
    CouchbaseRequestInfoHolder holder = context.get(KEY);
    if (holder != null) {
      return holder.couchbaseRequest;
    }
    return null;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
