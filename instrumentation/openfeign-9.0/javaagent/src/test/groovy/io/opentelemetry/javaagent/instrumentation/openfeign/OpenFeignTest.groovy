/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign

import feign.Feign


import feign.Target
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

class OpenFeignTest extends HttpClientTest<OpenFeignTestingApi> implements AgentTestTrait {


  @Override
  final OpenFeignTestingApi buildRequest(String method, URI uri, Map<String, String> headers) {
    Map<String, Collection<String>> feignHeaders = new HashMap<>(headers.size())
    headers.forEach((k, v) -> {
      feignHeaders.put(k, Collections.singleton(v))
    })
    Target<OpenFeignTestingApi> target = new OpenFeignTestingOnceTarget(method, uri.toString(), feignHeaders)
    return Feign.builder().target(target)
  }

  @Override
  final int sendRequest(OpenFeignTestingApi api, String method, URI uri,
                        Map<String, String> headers) {
    String result = api.testing()
    return result != null ? 200 : 500
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    //constant span name in `OpenFeignTestingOnceTarget#name()`
    return "GET OpenfeignTestingApi"
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    def attributes = super.httpAttributes(uri)
    attributes.remove(SemanticAttributes.HTTP_FLAVOR)
    attributes.remove(SemanticAttributes.NET_PEER_PORT)
    attributes.remove(SemanticAttributes.HTTP_METHOD)
    attributes
  }

  @Override
  boolean testCallback() {
    false
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }

  @Override
  boolean testCausality() {
    false
  }

  @Override
  boolean testWithClientParent() {
    false
  }
}
