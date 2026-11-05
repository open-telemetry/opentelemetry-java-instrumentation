/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

class HttpUrlState {
  @Nullable Context context;
  boolean finished;
  // by default 0 is ignored
  int statusCode = 0;

  HttpUrlState(Context context) {
    this.context = context;
  }

  void finish() {
    finished = true;
    // clear context to avoid potential leak when virtual field fallback map is used
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16129
    context = null;
  }
}
