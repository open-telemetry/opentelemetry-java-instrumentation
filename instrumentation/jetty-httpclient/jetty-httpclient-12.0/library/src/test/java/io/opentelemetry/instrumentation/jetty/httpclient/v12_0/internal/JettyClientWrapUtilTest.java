/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.thread.Invocable;
import org.eclipse.jetty.util.thread.Invocable.InvocationType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JettyClientWrapUtilTest {

  @ParameterizedTest
  @EnumSource(InvocationType.class)
  void demandPreservesInvocationTypeAndContext(InvocationType invocationType) {
    ContextKey<String> key = ContextKey.named("jetty-demand-test");
    Context callerContext = Context.root().with(key, "caller");
    Context deliveryContext = Context.root().with(key, "delivery");
    AtomicReference<Runnable> delegatedCallback = new AtomicReference<>();
    Content.Source source =
        new Content.Source() {
          @Override
          public Content.Chunk read() {
            return null;
          }

          @Override
          public void demand(Runnable callback) {
            delegatedCallback.set(callback);
          }

          @Override
          public void fail(Throwable failure) {}
        };
    Runnable callback =
        Invocable.from(
            invocationType, () -> assertThat(Context.current().get(key)).isEqualTo("caller"));

    JettyClientWrapUtil.wrapContentSource(source, callerContext).demand(callback);

    Runnable wrappedCallback = delegatedCallback.get();
    assertThat(wrappedCallback).isNotNull();
    assertThat(Invocable.getInvocationType(wrappedCallback)).isEqualTo(invocationType);
    try (Scope ignored = deliveryContext.makeCurrent()) {
      wrappedCallback.run();
      assertThat(Context.current().get(key)).isEqualTo("delivery");
    }
  }
}
