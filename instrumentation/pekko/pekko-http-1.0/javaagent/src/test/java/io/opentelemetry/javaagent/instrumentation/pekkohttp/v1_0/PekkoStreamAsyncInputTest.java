/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.Outlet;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.stage.AbstractOutHandler;
import org.apache.pekko.stream.stage.AsyncCallback;
import org.apache.pekko.stream.stage.GraphStage;
import org.apache.pekko.stream.stage.GraphStageLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PekkoStreamAsyncInputTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void streamWakeupDoesNotInheritSubmittingSpan() throws Exception {
    CompletableFuture<AsyncCallback<String>> ready = new CompletableFuture<>();
    CompletableFuture<Boolean> callbackHasSpan = new CompletableFuture<>();
    ActorSystem system = ActorSystem.create("async-input-test");
    try {
      Source.fromGraph(
              new GraphStage<SourceShape<String>>() {
                private final Outlet<String> outlet = Outlet.create("async-input");
                private final SourceShape<String> shape = SourceShape.of(outlet);

                @Override
                public SourceShape<String> shape() {
                  return shape;
                }

                @Override
                public GraphStageLogic createLogic(Attributes inheritedAttributes) {
                  return new GraphStageLogic(shape) {
                    {
                      setHandler(
                          outlet,
                          new AbstractOutHandler() {
                            @Override
                            public void onPull() {}
                          });
                    }

                    @Override
                    public void preStart() {
                      ready.complete(
                          createAsyncCallback(ignored -> {
                            callbackHasSpan.complete(Span.current().getSpanContext().isValid());
                            completeStage();
                          }));
                    }
                  };
                }
              })
          .runWith(Sink.ignore(), system);

      AsyncCallback<String> callback = ready.get(10, SECONDS);
      testing.runWithSpan("request", () -> callback.invoke("wake up"));

      assertThat(callbackHasSpan.get(10, SECONDS)).isFalse();
    } finally {
      system.terminate();
    }
  }
}
