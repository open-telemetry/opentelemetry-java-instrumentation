/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import com.couchbase.client.core.message.CouchbaseRequest;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseRequestInfo;

public class VirtualFieldHelper {
  public static final VirtualField<CouchbaseRequest, CouchbaseRequestInfo> COUCHBASE_REQUEST_INFO =
      VirtualField.find(CouchbaseRequest.class, CouchbaseRequestInfo.class);

  private VirtualFieldHelper() {}
}
