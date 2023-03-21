/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerResponseBeforeCommitHandler;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;

public enum NettyHttpServerResponseBeforeCommitHandler
    implements HttpServerResponseBeforeCommitHandler {
  INSTANCE;

  @Override
  public void handle(Context context, HttpResponse response) {
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, response, NettyHttpServerResponseMutator.INSTANCE);
  }
}
