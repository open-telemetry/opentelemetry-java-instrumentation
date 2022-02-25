/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class CouchbaseNetAttributesGetter
    implements NetClientAttributesGetter<CouchbaseRequestInfo, Void> {
  @Nullable
  @Override
  public String transport(CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    return couchbaseRequest.getPeerName() != null
        ? SemanticAttributes.NetTransportValues.IP_TCP
        : null;
  }

  @Nullable
  @Override
  public String peerName(CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    return couchbaseRequest.getPeerName();
  }

  @Nullable
  @Override
  public Integer peerPort(CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    return couchbaseRequest.getPeerPort();
  }

  @Nullable
  @Override
  public String peerIp(CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    return null;
  }
}
