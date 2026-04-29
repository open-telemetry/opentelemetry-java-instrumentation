/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import static java.util.Collections.emptyIterator;

import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;

final class RequestContextGetter implements TextMapGetter<ServiceRequestContext> {

  @Override
  public Iterable<String> keys(ServiceRequestContext carrier) {
    return () -> new HeaderNamesIterator(carrier.request().headers().names().iterator());
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
      return emptyIterator();
    }
    return carrier.request().headers().valueIterator(key);
  }

  private static final class HeaderNamesIterator implements Iterator<String> {

    private final Iterator<AsciiString> delegate;

    HeaderNamesIterator(Iterator<AsciiString> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public String next() {
      return delegate.next().toString();
    }
  }
}
