/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_5.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.restlet.Request;

final class RestletHeadersGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    return carrier.getHeaders().getNames();
  }

  @Override
  @Nullable
  public String get(@Nullable Request carrier, String key) {
    return carrier == null ? null : carrier.getHeaders().getFirstValue(key, /* ignoreCase= */ true);
  }

  @Override
  public Iterator<String> getAll(@Nullable Request carrier, String key) {
    return carrier == null
        ? emptyIterator()
        : asList(carrier.getHeaders().getValuesArray(key, /* ignoreCase= */ true)).iterator();
  }
}
