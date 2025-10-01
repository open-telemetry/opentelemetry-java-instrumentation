/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.context.Context;
import java.util.function.BiConsumer;

public final class OpenSearchResponseHandler implements BiConsumer<Object, Throwable> {
  private final Context context;
  private final OpenSearchRequest otelRequest;

  public OpenSearchResponseHandler(Context context, OpenSearchRequest otelRequest) {
    this.context = context;
    this.otelRequest = otelRequest;
  }

  @Override
  public void accept(Object response, Throwable error) {
    // OpenSearch responses don't provide response information, so the span is closed with null.
    OpenSearchSingletons.instrumenter().end(context, otelRequest, null, error);
  }
}
