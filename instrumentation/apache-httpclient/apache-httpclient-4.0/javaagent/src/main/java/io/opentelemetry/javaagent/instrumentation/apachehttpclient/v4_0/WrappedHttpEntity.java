/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.context.Context;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class WrappedHttpEntity extends HttpEntityWrapper {
  private final Context parentContext;

  public WrappedHttpEntity(Context parentContext, HttpEntity delegate) {
    super(delegate);
    this.parentContext = parentContext;
  }

  @Override
  public void writeTo(OutputStream outStream) throws IOException {
    super.writeTo(new CountingOutputStream(parentContext, outStream));
  }
}
