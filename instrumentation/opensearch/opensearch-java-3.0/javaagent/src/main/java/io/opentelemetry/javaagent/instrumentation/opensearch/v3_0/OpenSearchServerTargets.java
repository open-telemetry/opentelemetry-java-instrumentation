/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import java.util.List;
import javax.annotation.Nullable;
import org.opensearch.client.transport.OpenSearchTransport;

public class OpenSearchServerTargets {

  private static final VirtualField<OpenSearchTransport, DbServerTarget> SERVER_TARGET =
      VirtualField.find(OpenSearchTransport.class, DbServerTarget.class);

  public static void capture(
      OpenSearchTransport transport, @Nullable List<OpenSearchServerTarget.Endpoint> endpoints) {
    capture(transport, OpenSearchServerTarget.of(endpoints));
  }

  public static void capture(OpenSearchTransport transport, @Nullable DbServerTarget target) {
    if (target == null) {
      return;
    }
    SERVER_TARGET.set(transport, target);
  }

  @Nullable
  public static DbServerTarget get(OpenSearchTransport transport) {
    return SERVER_TARGET.get(transport);
  }

  private OpenSearchServerTargets() {}
}
