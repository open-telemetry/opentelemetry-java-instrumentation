/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;

class LibertyDispatcherRequestGetter implements TextMapGetter<LibertyRequest> {

  @Override
  public Iterable<String> keys(LibertyRequest carrier) {
    return carrier.getAllHeaderNames();
  }

  @Override
  public String get(@Nullable LibertyRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getHeaderValue(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable LibertyRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    return carrier.getHeaderValues(key).iterator();
  }
}
