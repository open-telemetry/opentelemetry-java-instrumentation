/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.newSetFromMap;

import java.util.IdentityHashMap;
import java.util.Set;

class SpymemcachedRequestSet {

  private final Set<SpymemcachedRequest> requests =
      newSetFromMap(new IdentityHashMap<SpymemcachedRequest, Boolean>());

  void add(SpymemcachedRequest request) {
    requests.add(request);
  }

  void merge(SpymemcachedRequestSet other) {
    requests.addAll(other.requests);
  }

  Iterable<SpymemcachedRequest> requests() {
    return requests;
  }
}
