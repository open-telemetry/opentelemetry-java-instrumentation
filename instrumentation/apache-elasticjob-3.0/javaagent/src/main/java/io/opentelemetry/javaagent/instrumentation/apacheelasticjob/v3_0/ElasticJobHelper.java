/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

public final class ElasticJobHelper {
  private final Instrumenter<ElasticJobProcessRequest, Void> instrumenter;

  private ElasticJobHelper(Instrumenter<ElasticJobProcessRequest, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public static ElasticJobHelper create(Instrumenter<ElasticJobProcessRequest, Void> instrumenter) {
    return new ElasticJobHelper(instrumenter);
  }

  @Nullable
  public ElasticJobScope startSpan(ElasticJobProcessRequest request) {
    Context parentContext = Context.current();
    if (!this.instrumenter.shouldStart(parentContext, request)) {
      return null;
    } else {
      Context context = this.instrumenter.start(parentContext, request);
      return new ElasticJobScope(request, context, context.makeCurrent());
    }
  }

  public void endSpan(@Nullable ElasticJobScope scope, @Nullable Throwable throwable) {
    if (scope != null) {
      scope.scope.close();
      this.instrumenter.end(scope.context, scope.request, null, throwable);
    }
  }

  public static class ElasticJobScope {
    private final ElasticJobProcessRequest request;
    private final Context context;
    private final Scope scope;

    private ElasticJobScope(ElasticJobProcessRequest request, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.scope = scope;
    }
  }
}
