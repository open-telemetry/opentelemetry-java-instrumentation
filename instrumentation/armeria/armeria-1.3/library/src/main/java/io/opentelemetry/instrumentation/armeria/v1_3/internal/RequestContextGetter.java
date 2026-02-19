/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import static java.util.stream.Collectors.toList;

import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Iterator;
import javax.annotation.Nullable;

enum RequestContextGetter implements TextMapGetter<ServiceRequestContext> {
  INSTANCE;

  @Override
  public Iterable<String> keys(@Nullable ServiceRequestContext carrier) {
    if (carrier == null) {
      return Collections.emptyList();
    }
    return carrier.request().headers().names().stream()
        .map(AsciiString::toString)
        .collect(toList());
  }

  @Override
  @Nullable
  public String get(@Nullable ServiceRequestContext carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.request().headers().get(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable ServiceRequestContext carrier, String key) {
    if (carrier == null) {
      return Collections.emptyIterator();
    }
    return carrier.request().headers().valueIterator(key);
  }
}
