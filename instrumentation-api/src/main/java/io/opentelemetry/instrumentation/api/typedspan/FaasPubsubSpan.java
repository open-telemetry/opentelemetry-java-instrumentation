/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class FaasPubsubSpan extends DelegatingSpan implements FaasPubsubSemanticConvention {

  protected FaasPubsubSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link FaasPubsubSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasPubsubSpan} object.
   */
  public static FaasPubsubSpanBuilder createFaasPubsubSpan(Tracer tracer, String spanName) {
    return new FaasPubsubSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link FaasPubsubSpan} from a {@link FaasSpanSpan}.
   *
   * @param builder {@link FaasSpanSpan.FaasSpanSpanBuilder} to use.
   * @return a {@link FaasPubsubSpan} object built from a {@link FaasSpanSpan}.
   */
  public static FaasPubsubSpanBuilder createFaasPubsubSpan(
      FaasSpanSpan.FaasSpanSpanBuilder builder) {
    // we accept a builder from FaasSpan since FaasPubsub "extends" FaasSpan
    return new FaasPubsubSpanBuilder(builder.getSpanBuilder());
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
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasPubsubSemanticConvention setFaasTrigger(String faasTrigger) {
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasPubsubSemanticConvention setFaasExecution(String faasExecution) {
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets messaging.system.
   *
   * @param messagingSystem A string identifying the messaging system.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingSystem(String messagingSystem) {
    delegate.setAttribute("messaging.system", messagingSystem);
    return this;
  }

  /**
   * Sets messaging.destination.
   *
   * @param messagingDestination The message destination name. This might be equal to the span name
   *     but is required nevertheless.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingDestination(String messagingDestination) {
    delegate.setAttribute("messaging.destination", messagingDestination);
    return this;
  }

  /**
   * Sets messaging.destination_kind.
   *
   * @param messagingDestinationKind The kind of message destination.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingDestinationKind(String messagingDestinationKind) {
    delegate.setAttribute("messaging.destination_kind", messagingDestinationKind);
    return this;
  }

  /**
   * Sets messaging.temp_destination.
   *
   * @param messagingTempDestination A boolean that is true if the message destination is temporary.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingTempDestination(
      boolean messagingTempDestination) {
    delegate.setAttribute("messaging.temp_destination", messagingTempDestination);
    return this;
  }

  /**
   * Sets messaging.protocol.
   *
   * @param messagingProtocol The name of the transport protocol.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingProtocol(String messagingProtocol) {
    delegate.setAttribute("messaging.protocol", messagingProtocol);
    return this;
  }

  /**
   * Sets messaging.protocol_version.
   *
   * @param messagingProtocolVersion The version of the transport protocol.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingProtocolVersion(String messagingProtocolVersion) {
    delegate.setAttribute("messaging.protocol_version", messagingProtocolVersion);
    return this;
  }

  /**
   * Sets messaging.url.
   *
   * @param messagingUrl Connection string.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingUrl(String messagingUrl) {
    delegate.setAttribute("messaging.url", messagingUrl);
    return this;
  }

  /**
   * Sets messaging.message_id.
   *
   * @param messagingMessageId A value used by the messaging system as an identifier for the
   *     message, represented as a string.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingMessageId(String messagingMessageId) {
    delegate.setAttribute("messaging.message_id", messagingMessageId);
    return this;
  }

  /**
   * Sets messaging.conversation_id.
   *
   * @param messagingConversationId A value identifying the conversation to which the message
   *     belongs, represented as a string. Sometimes called "Correlation ID".
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingConversationId(String messagingConversationId) {
    delegate.setAttribute("messaging.conversation_id", messagingConversationId);
    return this;
  }

  /**
   * Sets messaging.message_payload_size_bytes.
   *
   * @param messagingMessagePayloadSizeBytes The (uncompressed) size of the message payload in
   *     bytes. Also use this attribute if it is unknown whether the compressed or uncompressed
   *     payload size is reported.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingMessagePayloadSizeBytes(
      long messagingMessagePayloadSizeBytes) {
    delegate.setAttribute("messaging.message_payload_size_bytes", messagingMessagePayloadSizeBytes);
    return this;
  }

  /**
   * Sets messaging.message_payload_compressed_size_bytes.
   *
   * @param messagingMessagePayloadCompressedSizeBytes The compressed size of the message payload in
   *     bytes.
   */
  @Override
  public FaasPubsubSemanticConvention setMessagingMessagePayloadCompressedSizeBytes(
      long messagingMessagePayloadCompressedSizeBytes) {
    delegate.setAttribute(
        "messaging.message_payload_compressed_size_bytes",
        messagingMessagePayloadCompressedSizeBytes);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public FaasPubsubSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Strongly recommended for in-process queueing systems.
   */
  @Override
  public FaasPubsubSemanticConvention setNetTransport(String netTransport) {
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
  public FaasPubsubSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public FaasPubsubSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public FaasPubsubSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute(NET_HOST_IP, netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public FaasPubsubSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute(NET_HOST_PORT, netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public FaasPubsubSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute(NET_HOST_NAME, netHostName);
    return this;
  }

  /** Builder class for {@link FaasPubsubSpan}. */
  public static class FaasPubsubSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected FaasPubsubSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasPubsubSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasPubsubSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasPubsubSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasPubsubSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasPubsubSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasPubsubSpanBuilder setFaasTrigger(String faasTrigger) {
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasPubsubSpanBuilder setFaasExecution(String faasExecution) {
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets messaging.system.
     *
     * @param messagingSystem A string identifying the messaging system.
     */
    public FaasPubsubSpanBuilder setMessagingSystem(String messagingSystem) {
      internalBuilder.setAttribute("messaging.system", messagingSystem);
      return this;
    }

    /**
     * Sets messaging.destination.
     *
     * @param messagingDestination The message destination name. This might be equal to the span
     *     name but is required nevertheless.
     */
    public FaasPubsubSpanBuilder setMessagingDestination(String messagingDestination) {
      internalBuilder.setAttribute("messaging.destination", messagingDestination);
      return this;
    }

    /**
     * Sets messaging.destination_kind.
     *
     * @param messagingDestinationKind The kind of message destination.
     */
    public FaasPubsubSpanBuilder setMessagingDestinationKind(String messagingDestinationKind) {
      internalBuilder.setAttribute("messaging.destination_kind", messagingDestinationKind);
      return this;
    }

    /**
     * Sets messaging.temp_destination.
     *
     * @param messagingTempDestination A boolean that is true if the message destination is
     *     temporary.
     */
    public FaasPubsubSpanBuilder setMessagingTempDestination(boolean messagingTempDestination) {
      internalBuilder.setAttribute("messaging.temp_destination", messagingTempDestination);
      return this;
    }

    /**
     * Sets messaging.protocol.
     *
     * @param messagingProtocol The name of the transport protocol.
     */
    public FaasPubsubSpanBuilder setMessagingProtocol(String messagingProtocol) {
      internalBuilder.setAttribute("messaging.protocol", messagingProtocol);
      return this;
    }

    /**
     * Sets messaging.protocol_version.
     *
     * @param messagingProtocolVersion The version of the transport protocol.
     */
    public FaasPubsubSpanBuilder setMessagingProtocolVersion(String messagingProtocolVersion) {
      internalBuilder.setAttribute("messaging.protocol_version", messagingProtocolVersion);
      return this;
    }

    /**
     * Sets messaging.url.
     *
     * @param messagingUrl Connection string.
     */
    public FaasPubsubSpanBuilder setMessagingUrl(String messagingUrl) {
      internalBuilder.setAttribute("messaging.url", messagingUrl);
      return this;
    }

    /**
     * Sets messaging.message_id.
     *
     * @param messagingMessageId A value used by the messaging system as an identifier for the
     *     message, represented as a string.
     */
    public FaasPubsubSpanBuilder setMessagingMessageId(String messagingMessageId) {
      internalBuilder.setAttribute("messaging.message_id", messagingMessageId);
      return this;
    }

    /**
     * Sets messaging.conversation_id.
     *
     * @param messagingConversationId A value identifying the conversation to which the message
     *     belongs, represented as a string. Sometimes called "Correlation ID".
     */
    public FaasPubsubSpanBuilder setMessagingConversationId(String messagingConversationId) {
      internalBuilder.setAttribute("messaging.conversation_id", messagingConversationId);
      return this;
    }

    /**
     * Sets messaging.message_payload_size_bytes.
     *
     * @param messagingMessagePayloadSizeBytes The (uncompressed) size of the message payload in
     *     bytes. Also use this attribute if it is unknown whether the compressed or uncompressed
     *     payload size is reported.
     */
    public FaasPubsubSpanBuilder setMessagingMessagePayloadSizeBytes(
        long messagingMessagePayloadSizeBytes) {
      internalBuilder.setAttribute(
          "messaging.message_payload_size_bytes", messagingMessagePayloadSizeBytes);
      return this;
    }

    /**
     * Sets messaging.message_payload_compressed_size_bytes.
     *
     * @param messagingMessagePayloadCompressedSizeBytes The compressed size of the message payload
     *     in bytes.
     */
    public FaasPubsubSpanBuilder setMessagingMessagePayloadCompressedSizeBytes(
        long messagingMessagePayloadCompressedSizeBytes) {
      internalBuilder.setAttribute(
          "messaging.message_payload_compressed_size_bytes",
          messagingMessagePayloadCompressedSizeBytes);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public FaasPubsubSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Strongly recommended for in-process queueing systems.
     */
    public FaasPubsubSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public FaasPubsubSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public FaasPubsubSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public FaasPubsubSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute(NET_HOST_IP, netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public FaasPubsubSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute(NET_HOST_PORT, netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public FaasPubsubSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute(NET_HOST_NAME, netHostName);
      return this;
    }
  }
}
