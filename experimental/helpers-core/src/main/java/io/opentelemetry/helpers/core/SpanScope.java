package io.opentelemetry.helpers.core;

import io.opentelemetry.context.Scope;
import io.opentelemetry.distributedcontext.DistributedContext;
import io.opentelemetry.trace.Span;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Holds all the info about an active span plus provides behavior support.
 *
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public interface SpanScope<Q, P> extends Scope {

  /**
   * Records <code>net.peer.*</code> span attributes.
   *
   * @param remoteConnection the socket address
   */
  void onPeerConnection(InetSocketAddress remoteConnection);

  /**
   * Records <code>net.peer.*</code> span attributes.
   *
   * @param remoteAddress the remote address
   */
  void onPeerConnection(InetAddress remoteAddress);

  /**
   * Records data about one message received.
   *
   * @param message the message
   * @param <M> the type representing the message
   * @return the supplied message for further downstream processing
   */
  <M> M onMessageReceived(M message);

  /**
   * Records data about one message sent.
   *
   * @param message the message
   * @param <M> the type representing the message
   * @return the supplied message for further downstream processing
   */
  <M> M onMessageSent(M message);

  /**
   * Records the successful completion of the span
   *
   * @param response the response if there is one
   */
  void onSuccess(P response);

  /**
   * Records an error condition which prevented the successful completion of the span.
   *
   * @param throwable the cause of the failure
   * @param response the response if there is one
   */
  void onError(Throwable throwable, P response);

  /**
   * Returns the active span this object wraps.
   *
   * @return the span
   */
  Span getSpan();

  /**
   * Returns the correlation context active during the span.
   *
   * @return the correlation context
   */
  DistributedContext getCorrelationContext();
}
