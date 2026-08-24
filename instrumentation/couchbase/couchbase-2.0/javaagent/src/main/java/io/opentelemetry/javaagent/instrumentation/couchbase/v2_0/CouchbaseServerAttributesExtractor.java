/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo.Endpoint;
import javax.annotation.Nullable;

final class CouchbaseServerAttributesExtractor
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
    Endpoint endpoint = request.getEndpoint();
    if (endpoint == null) {
      return;
    }

    attributes.put(SERVER_ADDRESS, endpoint.getServerAddress());
    int serverPort = endpoint.getServerPort();
    if (serverPort > 0) {
      attributes.put(SERVER_PORT, serverPort);
    }
  }
}
