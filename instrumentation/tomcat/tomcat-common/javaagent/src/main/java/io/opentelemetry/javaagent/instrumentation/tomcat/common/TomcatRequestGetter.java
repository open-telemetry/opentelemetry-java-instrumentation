/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import org.apache.coyote.Request;

enum TomcatRequestGetter implements TextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request carrier) {
    return Collections.list(carrier.getMimeHeaders().names());
  }

  @Override
  public String get(Request carrier, String key) {
    return carrier.getHeader(key);
  }
}
