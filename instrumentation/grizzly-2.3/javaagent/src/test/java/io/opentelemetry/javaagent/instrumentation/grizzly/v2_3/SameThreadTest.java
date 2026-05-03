/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.v2_3;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;

class SameThreadTest extends GrizzlyIoStrategyTest {

  @Override
  protected AggregatedHttpRequest request(ServerEndpoint uri, String method) {
    AggregatedHttpRequest request = super.request(uri, method);
    if (testLatestDeps()) {
      return request;
    }
    // Older Grizzly versions may race SameThreadIOStrategy keep-alive reuse with a
    // server-side close, so avoid pooled client connection reuse in these shared tests.
    return AggregatedHttpRequest.of(
        request.headers().toBuilder().set(HttpHeaderNames.CONNECTION, "close").build());
  }

  @Override
  IOStrategy strategy() {
    return SameThreadIOStrategy.getInstance();
  }
}
