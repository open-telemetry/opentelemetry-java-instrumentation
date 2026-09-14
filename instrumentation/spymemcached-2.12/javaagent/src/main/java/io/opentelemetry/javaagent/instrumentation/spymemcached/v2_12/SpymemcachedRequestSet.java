/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.newSetFromMap;

import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

class SpymemcachedRequestSet {

  private final Set<SpymemcachedRequest> requests =
      newSetFromMap(new IdentityHashMap<SpymemcachedRequest, Boolean>());
  private boolean optimized;

  void add(SpymemcachedRequest request) {
    requests.add(request);
  }

  void merge(SpymemcachedRequestSet other) {
    optimized = true;
    requests.addAll(other.requests);
  }

  @Nullable
  SpymemcachedRequest getSingleRequest() {
    return !optimized && requests.size() == 1 ? requests.iterator().next() : null;
  }

  Iterable<SpymemcachedRequest> requests() {
    return requests;
  }
}
