package datadog.trace.instrumentation.springwebflux.client;

import static datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;

import io.opentracing.Span;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

public class TracingClientResponseSubscriber implements CoreSubscriber<ClientResponse> {

  private final CoreSubscriber<? super ClientResponse> subscriber;
  private final ClientRequest clientRequest;
  private final Context context;
  private final Span span;

  public TracingClientResponseSubscriber(
      final CoreSubscriber<? super ClientResponse> subscriber,
      final ClientRequest clientRequest,
      final Context context,
      final Span span) {
    this.subscriber = subscriber;
    this.clientRequest = clientRequest;
    this.context = context.put(Span.class, span);
    this.span = span;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    DECORATE.onRequest(span, clientRequest);
    DECORATE.onPeerConnection(span, remoteAddress());

    subscriber.onSubscribe(
        new Subscription() {
          @Override
          public void request(final long n) {
            subscription.request(n);
          }

          @Override
          public void cancel() {
            DECORATE.onCancel(span);
            DECORATE.beforeFinish(span);
            subscription.cancel();
            span.finish();
          }
        });
  }

  private InetAddress remoteAddress() {
    try {
      return InetAddress.getByName(clientRequest.url().getHost());
    } catch (final UnknownHostException e) {
      return null;
    }
  }

  @Override
  public void onNext(final ClientResponse clientResponse) {
    try {
      subscriber.onNext(new ClientResponseWrapper(clientResponse, context));
    } finally {
      DECORATE.onResponse(span, clientResponse);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try {
      subscriber.onError(throwable);
    } finally {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onComplete() {
    try {
      subscriber.onComplete();
    } finally {
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public Context currentContext() {
    return context;
  }
}
