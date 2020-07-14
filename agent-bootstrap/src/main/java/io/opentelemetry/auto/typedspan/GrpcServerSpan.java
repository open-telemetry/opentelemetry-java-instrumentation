/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>rpc.service: The service name, must be equal to the $service part in the span name.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 * </ul>
 *
 * <b>Additional constraints</b>
 *
 * <p>At least one of the following must be set:
 *
 * <ul>
 *   <li>net.peer.ip
 *   <li>net.peer.name
 * </ul>
 */
public class GrpcServerSpan extends DelegatingSpan implements GrpcServerSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    RPC_SERVICE,
    NET_TRANSPORT,
    NET_PEER_IP,
    NET_PEER_NAME,
    NET_HOST_IP,
    NET_HOST_PORT,
    NET_HOST_NAME,
    NET_PEER_PORT;

    @SuppressWarnings("ImmutableEnumChecker")
    private long flag;

    AttributeStatus() {
      this.flag = 1L << this.ordinal();
    }

    public boolean isSet(AttributeStatus attribute) {
      return (this.flag & attribute.flag) > 0;
    }

    public void set(AttributeStatus attribute) {
      this.flag |= attribute.flag;
    }

    public void set(long attribute) {
      this.flag = attribute;
    }

    public long getValue() {
      return flag;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(GrpcServerSpan.class.getName());

  public final AttributeStatus status;

  protected GrpcServerSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link GrpcServerSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link GrpcServerSpan} object.
   */
  public static GrpcServerSpanBuilder createGrpcServerSpan(Tracer tracer, String spanName) {
    return new GrpcServerSpanBuilder(tracer, spanName).setKind(Kind.SERVER);
  }

  /**
   * Creates a {@link GrpcServerSpan} from a {@link RpcSpan}.
   *
   * @param builder {@link RpcSpan.RpcSpanBuilder} to use.
   * @return a {@link GrpcServerSpan} object built from a {@link RpcSpan}.
   */
  public static GrpcServerSpanBuilder createGrpcServerSpan(RpcSpan.RpcSpanBuilder builder) {
    // we accept a builder from Rpc since GrpcServer "extends" Rpc
    return new GrpcServerSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public void end() {
    delegate.end();

    // required attributes
    if (!this.status.isSet(AttributeStatus.RPC_SERVICE)) {
      logger.warning("Wrong usage - Span missing rpc.service attribute");
    }
    // extra constraints.
    {
      boolean flag =
          (!this.status.isSet(AttributeStatus.NET_PEER_IP))
              || (!this.status.isSet(AttributeStatus.NET_PEER_NAME));
      if (flag) {
        logger.info("Constraint not respected!");
      }
    }
    // conditional attributes
  }

  /**
   * Sets rpc.service.
   *
   * @param rpcService The service name, must be equal to the $service part in the span name..
   */
  @Override
  public GrpcServerSemanticConvention setRpcService(String rpcService) {
    status.set(AttributeStatus.RPC_SERVICE);
    delegate.setAttribute("rpc.service", rpcService);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below..
   */
  @Override
  public GrpcServerSemanticConvention setNetTransport(String netTransport) {
    status.set(AttributeStatus.NET_TRANSPORT);
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
    status.set(AttributeStatus.NET_PEER_IP);
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below..
   */
  @Override
  public GrpcServerSemanticConvention setNetPeerName(String netPeerName) {
    status.set(AttributeStatus.NET_PEER_NAME);
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
   */
  @Override
  public GrpcServerSemanticConvention setNetHostIp(String netHostIp) {
    status.set(AttributeStatus.NET_HOST_IP);
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port..
   */
  @Override
  public GrpcServerSemanticConvention setNetHostPort(long netHostPort) {
    status.set(AttributeStatus.NET_HOST_PORT);
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below..
   */
  @Override
  public GrpcServerSemanticConvention setNetHostName(String netHostName) {
    status.set(AttributeStatus.NET_HOST_NAME);
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort It describes the port the client is connecting from.
   */
  @Override
  public GrpcServerSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /** Builder class for {@link GrpcServerSpan}. */
  public static class GrpcServerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected GrpcServerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public GrpcServerSpanBuilder(Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public GrpcServerSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public GrpcServerSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public GrpcServerSpanBuilder setKind(Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public GrpcServerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new GrpcServerSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets rpc.service.
     *
     * @param rpcService The service name, must be equal to the $service part in the span name..
     */
    public GrpcServerSpanBuilder setRpcService(String rpcService) {
      status.set(AttributeStatus.RPC_SERVICE);
      internalBuilder.setAttribute("rpc.service", rpcService);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below..
     */
    public GrpcServerSpanBuilder setNetTransport(String netTransport) {
      status.set(AttributeStatus.NET_TRANSPORT);
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
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below..
     */
    public GrpcServerSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
     */
    public GrpcServerSpanBuilder setNetHostIp(String netHostIp) {
      status.set(AttributeStatus.NET_HOST_IP);
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port..
     */
    public GrpcServerSpanBuilder setNetHostPort(long netHostPort) {
      status.set(AttributeStatus.NET_HOST_PORT);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below..
     */
    public GrpcServerSpanBuilder setNetHostName(String netHostName) {
      status.set(AttributeStatus.NET_HOST_NAME);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort It describes the port the client is connecting from.
     */
    public GrpcServerSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }
  }
}
