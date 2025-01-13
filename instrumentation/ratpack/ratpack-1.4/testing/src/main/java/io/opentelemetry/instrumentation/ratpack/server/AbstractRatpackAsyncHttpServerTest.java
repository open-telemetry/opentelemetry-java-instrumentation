/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.util.function.Consumer;
import ratpack.exec.Promise;

public abstract class AbstractRatpackAsyncHttpServerTest extends AbstractRatpackHttpServerTest {

  @Override
  protected void process(ServerEndpoint endpoint, Consumer<ServerEndpoint> consumer) {
    Promise.sync(() -> endpoint).then(consumer::accept);
  }
}
