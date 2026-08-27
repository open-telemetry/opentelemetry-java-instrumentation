/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;

class SpymemcachedRequestAssociations {

  private final RequestList requests = new RequestList();
  private final Map<String, RequestList> requestsByKey = new HashMap<>();
  private final IdentityHashMap<SpymemcachedRequest, Set<String>> keysByRequest =
      new IdentityHashMap<>();

  static SpymemcachedRequestAssociations create() {
    return new SpymemcachedRequestAssociations();
  }

  static SpymemcachedRequestAssociations create(SpymemcachedRequest request) {
    SpymemcachedRequestAssociations associations = create();
    associations.addRequest(request);
    return associations;
  }

  private SpymemcachedRequestAssociations() {}

  SpymemcachedRequestAssociations forOperation(Operation operation) {
    SpymemcachedRequestAssociations associations = new SpymemcachedRequestAssociations();
    if (!(operation instanceof KeyedOperation) || requestsByKey.isEmpty()) {
      associations.addRequests(requests);
      if (operation instanceof KeyedOperation) {
        for (String key : ((KeyedOperation) operation).getKeys()) {
          associations.addRequests(key, requests);
        }
      }
      return associations;
    }

    for (String key : ((KeyedOperation) operation).getKeys()) {
      RequestList keyedRequests = requestsByKey.get(key);
      if (keyedRequests != null) {
        associations.addRequests(key, keyedRequests);
      }
    }
    if (associations.requests.isEmpty()) {
      associations.addRequests(requests);
    }
    return associations;
  }

  void merge(SpymemcachedRequestAssociations other) {
    addRequests(other.requests);
    for (Map.Entry<String, RequestList> entry : other.requestsByKey.entrySet()) {
      addRequests(entry.getKey(), entry.getValue());
    }
  }

  Iterable<SpymemcachedRequest> requests() {
    return requests.values();
  }

  boolean hasRequestKeysOutside(SpymemcachedRequest request, Collection<String> operationKeys) {
    Set<String> requestKeys = keysByRequest.get(request);
    if (requestKeys == null) {
      return false;
    }
    for (String key : requestKeys) {
      if (!operationKeys.contains(key)) {
        return true;
      }
    }
    return false;
  }

  private void addRequests(RequestList requests) {
    for (SpymemcachedRequest request : requests.values()) {
      addRequest(request);
    }
  }

  private void addRequests(String key, RequestList requests) {
    RequestList keyedRequests = requestsByKey.get(key);
    if (keyedRequests == null) {
      keyedRequests = new RequestList();
      requestsByKey.put(key, keyedRequests);
    }
    for (SpymemcachedRequest request : requests.values()) {
      addRequest(request);
      keyedRequests.add(request);
      Set<String> requestKeys = keysByRequest.get(request);
      if (requestKeys == null) {
        requestKeys = new HashSet<>();
        keysByRequest.put(request, requestKeys);
      }
      requestKeys.add(key);
    }
  }

  private void addRequest(SpymemcachedRequest request) {
    requests.add(request);
  }

  private static class RequestList {
    private final List<SpymemcachedRequest> values = new ArrayList<>();
    private final IdentityHashMap<SpymemcachedRequest, Boolean> seen = new IdentityHashMap<>();

    void add(SpymemcachedRequest request) {
      if (seen.put(request, Boolean.TRUE) == null) {
        values.add(request);
      }
    }

    boolean isEmpty() {
      return values.isEmpty();
    }

    Iterable<SpymemcachedRequest> values() {
      return values;
    }
  }
}
