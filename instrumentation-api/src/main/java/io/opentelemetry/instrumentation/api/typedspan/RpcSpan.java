/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.opentelemetry.context.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class RpcSpan extends DelegatingSpan implements RpcSemanticConvention {

  protected RpcSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link RpcSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link RpcSpan} object.
   */
  public static RpcSpanBuilder createRpcSpan(Tracer tracer, String spanName) {
    return new RpcSpanBuilder(tracer, spanName);
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public RpcSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute(NET_TRANSPORT, netTransport);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public RpcSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public RpcSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public RpcSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public RpcSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute(NET_HOST_IP, netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public RpcSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute(NET_HOST_PORT, netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public RpcSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute(NET_HOST_NAME, netHostName);
    return this;
  }

  /**
   * Sets rpc.service.
   *
   * @param rpcService The service name, must be equal to the $service part in the span name.
   */
  @Override
  public RpcSemanticConvention setRpcService(String rpcService) {
    delegate.setAttribute(RPC_SERVICE, rpcService);
    return this;
  }

  /** Builder class for {@link RpcSpan}. */
  public static class RpcSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected RpcSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public RpcSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public RpcSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public RpcSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public RpcSpan start() {
      // check for sampling relevant field here, but there are none.
      return new RpcSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public RpcSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public RpcSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public RpcSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public RpcSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public RpcSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute(NET_HOST_IP, netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public RpcSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute(NET_HOST_PORT, netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public RpcSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute(NET_HOST_NAME, netHostName);
      return this;
    }

    /**
     * Sets rpc.service.
     *
     * @param rpcService The service name, must be equal to the $service part in the span name.
     */
    public RpcSpanBuilder setRpcService(String rpcService) {
      internalBuilder.setAttribute(RPC_SERVICE, rpcService);
      return this;
    }
  }
}
