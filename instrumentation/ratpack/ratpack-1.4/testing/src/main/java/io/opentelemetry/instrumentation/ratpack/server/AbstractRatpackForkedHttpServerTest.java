/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import ratpack.exec.Execution;
import ratpack.exec.Operation;
import ratpack.exec.Promise;
import ratpack.exec.Result;
import ratpack.exec.util.ParallelBatch;
import ratpack.handling.Chain;

public abstract class AbstractRatpackForkedHttpServerTest extends AbstractRatpackHttpServerTest {

  private static final ServerEndpoint FORK_AND_YIELD_ALL =
      new ServerEndpoint(
          "FORK_AND_YIELD_ALL", "fork_and_yieldAll", SUCCESS.getStatus(), SUCCESS.getBody(), false);

  @Override
  protected void process(ServerEndpoint endpoint, Consumer<ServerEndpoint> consumer) {
    Promise.sync(() -> endpoint).fork().then(consumer::accept);
  }

  @Override
  protected void registerHandlers(Chain chain) throws Exception {
    chain.prefix(
        FORK_AND_YIELD_ALL.rawPath(),
        chain1 ->
            chain1.all(
                context -> {
                  Promise<ServerEndpoint> promise =
                      Promise.async(
                          upstream ->
                              Execution.fork()
                                  .start(
                                      Operation.of(
                                          () -> upstream.accept(Result.success(SUCCESS)))));
                  ParallelBatch.of(promise)
                      .yieldAll()
                      .flatMap(list -> Promise.sync(() -> list.get(0).getValue()))
                      .then(
                          endpoint ->
                              controller(
                                  endpoint,
                                  () ->
                                      context
                                          .getResponse()
                                          .status(endpoint.getStatus())
                                          .send(endpoint.getBody())));
                }));
  }

  @Test
  void forkAndYieldAll() {
    AggregatedHttpRequest request = request(FORK_AND_YIELD_ALL, "GET");
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span ->
                      span.hasName("GET /fork_and_yieldAll")
                          .hasKind(SpanKind.SERVER)
                          .hasNoParent());
              boolean hasHandlerSpan = hasHandlerSpan(FORK_AND_YIELD_ALL);
              if (hasHandlerSpan) {
                assertions.add(
                    span ->
                        span.hasName("/fork_and_yieldAll")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)));
              }
              assertions.add(
                  span ->
                      span.hasName("controller")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(hasHandlerSpan ? 1 : 0)));

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }
}
