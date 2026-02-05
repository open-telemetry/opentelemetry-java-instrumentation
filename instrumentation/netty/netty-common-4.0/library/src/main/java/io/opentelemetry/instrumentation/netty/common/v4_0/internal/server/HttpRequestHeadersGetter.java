/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.server;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum HttpRequestHeadersGetter implements TextMapGetter<NettyCommonRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(NettyCommonRequest carrier) {
    return carrier.getRequest().headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable NettyCommonRequest carrier, String key) {
    return carrier.getRequest().headers().get(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable NettyCommonRequest carrier, String key) {
    List<String> list = carrier.getRequest().headers().getAll(key);
    return list != null ? list.iterator() : Collections.emptyIterator();
  }
}
