/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.context.Context;

// everything is public since called directly from advice code
// (which is inlined into other packages)
public class HttpUrlState {
  public final Context context;
  public boolean finished;
  // by default 0 is ignored
  public int statusCode = 0;

  public HttpUrlState(Context context) {
    this.context = context;
  }
}
