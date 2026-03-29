/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.eclipse.jetty.server.Request;

class Jetty12TextMapGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    return carrier.getHeaders().getFieldNamesCollection();
  }

  @Override
  public String get(@Nullable Request carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getHeaders().get(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable Request carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    return EnumerationUtil.asIterator(carrier.getHeaders().getValues(key));
  }
}
