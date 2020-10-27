/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.opentelemetry.context.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcClientSpan extends DelegatingSpan implements GrpcClientSemanticConvention {

  protected GrpcClientSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link GrpcClientSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link GrpcClientSpan} object.
   */
  public static GrpcClientSpanBuilder createGrpcClientSpan(Tracer tracer, String spanName) {
    return new GrpcClientSpanBuilder(tracer, spanName).setKind(Span.Kind.CLIENT);
  }

  /**
   * Creates a {@link GrpcClientSpan} from a {@link RpcSpan}.
   *
   * @param builder {@link RpcSpan.RpcSpanBuilder} to use.
   * @return a {@link GrpcClientSpan} object built from a {@link RpcSpan}.
   */
  public static GrpcClientSpanBuilder createGrpcClientSpan(RpcSpan.RpcSpanBuilder builder) {
    // we accept a builder from Rpc since GrpcClient "extends" Rpc
    return new GrpcClientSpanBuilder(builder.getSpanBuilder());
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
  public GrpcClientSemanticConvention setNetTransport(String netTransport) {
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
  public GrpcClientSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort It describes the server port the client is connecting to.
   */
  @Override
  public GrpcClientSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public GrpcClientSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public GrpcClientSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute(NET_HOST_IP, netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public GrpcClientSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute(NET_HOST_PORT, netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public GrpcClientSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute(NET_HOST_NAME, netHostName);
    return this;
  }

  /**
   * Sets rpc.service.
   *
   * @param rpcService The service name, must be equal to the $service part in the span name.
   */
  @Override
  public GrpcClientSemanticConvention setRpcService(String rpcService) {
    delegate.setAttribute(RPC_SERVICE, rpcService);
    return this;
  }

  /** Builder class for {@link GrpcClientSpan}. */
  public static class GrpcClientSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected GrpcClientSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public GrpcClientSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public GrpcClientSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public GrpcClientSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public GrpcClientSpan start() {
      // check for sampling relevant field here, but there are none.
      return new GrpcClientSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public GrpcClientSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public GrpcClientSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort It describes the server port the client is connecting to.
     */
    public GrpcClientSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public GrpcClientSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public GrpcClientSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute(NET_HOST_IP, netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public GrpcClientSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute(NET_HOST_PORT, netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public GrpcClientSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute(NET_HOST_NAME, netHostName);
      return this;
    }

    /**
     * Sets rpc.service.
     *
     * @param rpcService The service name, must be equal to the $service part in the span name.
     */
    public GrpcClientSpanBuilder setRpcService(String rpcService) {
      internalBuilder.setAttribute(RPC_SERVICE, rpcService);
      return this;
    }
  }
}
