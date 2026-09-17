/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.HttpHeaders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class ArmeriaHeaderUtil {

  static Collection<String> getHeaderNames(HttpHeaders headers) {
    List<String> names = new ArrayList<>(headers.size());
    for (CharSequence name : headers.names()) {
      // armeria carries the method, path, scheme, authority and status as HTTP/2 pseudo-headers,
      // which are not HTTP headers
      if (name.length() != 0 && name.charAt(0) != ':') {
        names.add(name.toString());
      }
    }
    return names;
  }

  private ArmeriaHeaderUtil() {}
}
