/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.server;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpRequestHeadersGetter implements TextMapGetter<NettyCommonRequest> {

  @Override
  public Iterable<String> keys(NettyCommonRequest carrier) {
    return carrier.getRequest().headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable NettyCommonRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getRequest().headers().get(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable NettyCommonRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    List<String> list = carrier.getRequest().headers().getAll(key);
    return list.iterator();
  }
}
