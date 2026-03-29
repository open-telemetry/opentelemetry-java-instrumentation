/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.EnumerationUtil;
import java.util.Collections;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.apache.coyote.Request;

final class TomcatRequestGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    return Collections.list(carrier.getMimeHeaders().names());
  }

  @Override
  @Nullable
  public String get(@Nullable Request carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable Request carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    return EnumerationUtil.asIterator(carrier.getMimeHeaders().values(key));
  }
}
