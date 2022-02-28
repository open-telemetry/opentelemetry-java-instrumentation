/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public class ExperimentalAttributesExtractor
    implements AttributesExtractor<CouchbaseRequestInfo, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CouchbaseRequestInfo request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CouchbaseRequestInfo request,
      @Nullable Void response,
      @Nullable Throwable error) {
    attributes.put("couchbase.operation_id", request.getOperationId());
    attributes.put("couchbase.local.address", request.getLocalAddress());
  }
}
