/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import java.util.function.BiPredicate;

final class ServerSideServerPortCondition<REQUEST> implements BiPredicate<Integer, REQUEST> {

  private final HttpServerAttributesGetter<REQUEST, ?> getter;

  ServerSideServerPortCondition(HttpServerAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public boolean test(Integer port, REQUEST request) {
    String scheme = getter.getUrlScheme(request);
    if (scheme == null || port == null) {
      return true;
    }
    // according to spec: extract if not default (80 for http scheme, 443 for https).
    if ((scheme.equalsIgnoreCase("http") && port == 80)
        || (scheme.equalsIgnoreCase("https") && port == 443)) {
      return false;
    }
    return true;
  }
}
