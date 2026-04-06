/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import java.util.Iterator;
import javax.annotation.Nullable;

enum NettyHeadersGetter implements TextMapGetter<NettyRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(NettyRequest requestAndChannel) {
    return requestAndChannel.request().headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable NettyRequest requestAndChannel, String s) {
    if (requestAndChannel == null) {
      return null;
    }
    return requestAndChannel.request().headers().get(s);
  }

  @Override
  public Iterator<String> getAll(@Nullable NettyRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    return carrier.request().headers().getAll(key).iterator();
  }
}
