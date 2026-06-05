/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.RequestAndContext;
import javax.annotation.Nullable;

// Helper for accessing the virtual field on package-private Call.
public final class OpenTelemetryCallUtil {
  private static final VirtualField<Call, RequestAndContext> requestAndContextField =
      VirtualField.find(Call.class, RequestAndContext.class);

  public static void setRequestAndContext(
      Object call, @Nullable RequestAndContext requestAndContext) {
    requestAndContextField.set((Call) call, requestAndContext);
  }

  @Nullable
  public static RequestAndContext getAndClearRequestAndContext(Object call) {
    RequestAndContext requestAndContext = requestAndContextField.get((Call) call);
    requestAndContextField.set((Call) call, null);
    return requestAndContext;
  }

  private OpenTelemetryCallUtil() {}
}
