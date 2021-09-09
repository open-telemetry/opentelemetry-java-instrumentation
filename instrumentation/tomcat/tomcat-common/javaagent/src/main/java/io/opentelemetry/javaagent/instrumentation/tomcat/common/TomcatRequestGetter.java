/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import org.apache.coyote.Request;

public class TomcatRequestGetter implements TextMapGetter<Request> {

  public static final TomcatRequestGetter GETTER = new TomcatRequestGetter();

  @Override
  public Iterable<String> keys(Request carrier) {
    return Collections.list(carrier.getMimeHeaders().names());
  }

  @Override
  public String get(Request carrier, String key) {
    return carrier.getHeader(key);
  }
}
