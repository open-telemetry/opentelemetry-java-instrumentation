/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class VirtualFieldHelper {
  public static final VirtualField<ClusterFacade, DbServerTarget> COUCHBASE_SERVER_TARGET =
      VirtualField.find(ClusterFacade.class, DbServerTarget.class);

  private VirtualFieldHelper() {}
}
