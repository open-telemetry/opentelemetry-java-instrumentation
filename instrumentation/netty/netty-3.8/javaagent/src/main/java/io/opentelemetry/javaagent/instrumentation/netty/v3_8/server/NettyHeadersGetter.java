/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.util.Iterator;
import javax.annotation.Nullable;

enum NettyHeadersGetter implements ExtendedTextMapGetter<HttpRequestAndChannel> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpRequestAndChannel requestAndChannel, String s) {
    return requestAndChannel.request().headers().get(s);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpRequestAndChannel carrier, String key) {
    return carrier.request().headers().getAll(key).iterator();
  }
}
