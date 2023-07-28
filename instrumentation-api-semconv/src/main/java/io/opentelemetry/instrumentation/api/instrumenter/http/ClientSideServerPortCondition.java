/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import java.util.Locale;
import java.util.function.BiPredicate;

final class ClientSideServerPortCondition<REQUEST> implements BiPredicate<Integer, REQUEST> {

  private final HttpClientAttributesGetter<REQUEST, ?> getter;

  ClientSideServerPortCondition(HttpClientAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public boolean test(Integer port, REQUEST request) {
    String url = getter.getUrlFull(request);
    if (url == null || port == null) {
      return true;
    }
    url = url.toLowerCase(Locale.ROOT);
    // according to spec: extract if not default (80 for http scheme, 443 for https).
    if ((url.startsWith("http://") && port == 80) || (url.startsWith("https://") && port == 443)) {
      return false;
    }
    return true;
  }
}
