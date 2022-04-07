package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

class FluxWithSpanTest extends AbstractWithSpanTest<UnicastProcessor<String>, Flux<String>> {

  @Override
  protected AbstractTraced<UnicastProcessor<String>, Flux<String>> newTraced() {
    return new Traced();
  }

  @Override
  protected void complete(UnicastProcessor<String> future, String value) {
    future.onNext(value);
    future.onComplete();
  }

  @Override
  protected void fail(UnicastProcessor<String> future, Throwable error) {
    future.onError(error);
  }

  @Override
  protected void cancel(UnicastProcessor<String> future) {
    StepVerifier.create(future).expectSubscription().thenCancel().verify();
  }

  @Override
  protected String getCompleted(Flux<String> future) {
    return future.blockLast();
  }

  @Override
  protected Throwable unwrapError(Throwable t) {
    return t;
  }

  @Override
  protected String canceledKey() {
    return "reactor.canceled";
  }

  static class Traced extends AbstractTraced<UnicastProcessor<String>, Flux<String>> {

    @Override
    @WithSpan
    protected UnicastProcessor<String> completable() {
      return UnicastProcessor.create();
    }

    @Override
    @WithSpan
    protected Flux<String> alreadySucceeded() {
      return Flux.just(SUCCESS_VALUE);
    }

    @Override
    @WithSpan
    protected Flux<String> alreadyFailed() {
      return Flux.error(FAILURE);
    }
  }
}
