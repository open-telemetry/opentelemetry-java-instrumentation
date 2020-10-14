/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.context.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcServerSpan extends DelegatingSpan implements GrpcServerSemanticConvention {

  protected GrpcServerSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link GrpcServerSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link GrpcServerSpan} object.
   */
  public static GrpcServerSpanBuilder createGrpcServerSpan(Tracer tracer, String spanName) {
    return new GrpcServerSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
  }

  /**
   * Creates a {@link GrpcServerSpan} from a {@link RpcSpan}.
   *
   * @param builder {@link RpcSpan.RpcSpanBuilder} to use.
   * @return a {@link GrpcServerSpan} object built from a {@link RpcSpan}.
   */
  public static GrpcServerSpanBuilder createGrpcServerSpan(RpcSpan.RpcSpanBuilder builder) {
    // we accept a builder from Rpc since GrpcServer "extends" Rpc
    return new GrpcServerSpanBuilder(builder.getSpanBuilder());
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
  public GrpcServerSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public GrpcServerSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort It describes the port the client is connecting from.
   */
  @Override
  public GrpcServerSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public GrpcServerSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public GrpcServerSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public GrpcServerSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public GrpcServerSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /**
   * Sets rpc.service.
   *
   * @param rpcService The service name, must be equal to the $service part in the span name.
   */
  @Override
  public GrpcServerSemanticConvention setRpcService(String rpcService) {
    delegate.setAttribute("rpc.service", rpcService);
    return this;
  }

  /** Builder class for {@link GrpcServerSpan}. */
  public static class GrpcServerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected GrpcServerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public GrpcServerSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public GrpcServerSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public GrpcServerSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public GrpcServerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new GrpcServerSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public GrpcServerSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public GrpcServerSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort It describes the port the client is connecting from.
     */
    public GrpcServerSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public GrpcServerSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public GrpcServerSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public GrpcServerSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public GrpcServerSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

    /**
     * Sets rpc.service.
     *
     * @param rpcService The service name, must be equal to the $service part in the span name.
     */
    public GrpcServerSpanBuilder setRpcService(String rpcService) {
      internalBuilder.setAttribute("rpc.service", rpcService);
      return this;
    }
  }
}
