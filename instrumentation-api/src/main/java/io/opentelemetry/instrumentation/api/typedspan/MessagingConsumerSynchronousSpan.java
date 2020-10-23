/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public class MessagingConsumerSynchronousSpan extends DelegatingSpan
    implements MessagingConsumerSynchronousSemanticConvention {

  protected MessagingConsumerSynchronousSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link MessagingConsumerSynchronousSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link MessagingConsumerSynchronousSpan} object.
   */
  public static MessagingConsumerSynchronousSpanBuilder createMessagingConsumerSynchronousSpan(
      Tracer tracer, String spanName) {
    return new MessagingConsumerSynchronousSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
  }

  /**
   * Creates a {@link MessagingConsumerSynchronousSpan} from a {@link MessagingConsumerSpan}.
   *
   * @param builder {@link MessagingConsumerSpan.MessagingConsumerSpanBuilder} to use.
   * @return a {@link MessagingConsumerSynchronousSpan} object built from a {@link
   *     MessagingConsumerSpan}.
   */
  public static MessagingConsumerSynchronousSpanBuilder createMessagingConsumerSynchronousSpan(
      MessagingConsumerSpan.MessagingConsumerSpanBuilder builder) {
    // we accept a builder from MessagingConsumer since MessagingConsumerSynchronous "extends"
    // MessagingConsumer
    return new MessagingConsumerSynchronousSpanBuilder(builder.getSpanBuilder());
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
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute(NET_HOST_IP, netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute(NET_HOST_PORT, netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute(NET_HOST_NAME, netHostName);
    return this;
  }

  /**
   * Sets messaging.system.
   *
   * @param messagingSystem A string identifying the messaging system.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingSystem(String messagingSystem) {
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
  public MessagingConsumerSynchronousSemanticConvention setMessagingDestination(
      String messagingDestination) {
    delegate.setAttribute("messaging.destination", messagingDestination);
    return this;
  }

  /**
   * Sets messaging.destination_kind.
   *
   * @param messagingDestinationKind The kind of message destination.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingDestinationKind(
      String messagingDestinationKind) {
    delegate.setAttribute("messaging.destination_kind", messagingDestinationKind);
    return this;
  }

  /**
   * Sets messaging.temp_destination.
   *
   * @param messagingTempDestination A boolean that is true if the message destination is temporary.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingTempDestination(
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
  public MessagingConsumerSynchronousSemanticConvention setMessagingProtocol(
      String messagingProtocol) {
    delegate.setAttribute("messaging.protocol", messagingProtocol);
    return this;
  }

  /**
   * Sets messaging.protocol_version.
   *
   * @param messagingProtocolVersion The version of the transport protocol.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingProtocolVersion(
      String messagingProtocolVersion) {
    delegate.setAttribute("messaging.protocol_version", messagingProtocolVersion);
    return this;
  }

  /**
   * Sets messaging.url.
   *
   * @param messagingUrl Connection string.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingUrl(String messagingUrl) {
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
  public MessagingConsumerSynchronousSemanticConvention setMessagingMessageId(
      String messagingMessageId) {
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
  public MessagingConsumerSynchronousSemanticConvention setMessagingConversationId(
      String messagingConversationId) {
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
  public MessagingConsumerSynchronousSemanticConvention setMessagingMessagePayloadSizeBytes(
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
  public MessagingConsumerSynchronousSemanticConvention
      setMessagingMessagePayloadCompressedSizeBytes(
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
  public MessagingConsumerSynchronousSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Strongly recommended for in-process queueing systems.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute(NET_TRANSPORT, netTransport);
    return this;
  }

  /**
   * Sets messaging.operation.
   *
   * @param messagingOperation A string identifying which part and kind of message consumption this
   *     span describes.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingOperation(
      String messagingOperation) {
    delegate.setAttribute(SemanticAttributes.MESSAGING_OPERATION, messagingOperation);
    return this;
  }

  /** Builder class for {@link MessagingConsumerSynchronousSpan}. */
  public static class MessagingConsumerSynchronousSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected MessagingConsumerSynchronousSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public MessagingConsumerSynchronousSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public MessagingConsumerSynchronousSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public MessagingConsumerSynchronousSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public MessagingConsumerSynchronousSpan start() {
      // check for sampling relevant field here, but there are none.
      return new MessagingConsumerSynchronousSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public MessagingConsumerSynchronousSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute(NET_HOST_IP, netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute(NET_HOST_PORT, netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute(NET_HOST_NAME, netHostName);
      return this;
    }

    /**
     * Sets messaging.system.
     *
     * @param messagingSystem A string identifying the messaging system.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingSystem(String messagingSystem) {
      internalBuilder.setAttribute("messaging.system", messagingSystem);
      return this;
    }

    /**
     * Sets messaging.destination.
     *
     * @param messagingDestination The message destination name. This might be equal to the span
     *     name but is required nevertheless.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingDestination(
        String messagingDestination) {
      internalBuilder.setAttribute("messaging.destination", messagingDestination);
      return this;
    }

    /**
     * Sets messaging.destination_kind.
     *
     * @param messagingDestinationKind The kind of message destination.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingDestinationKind(
        String messagingDestinationKind) {
      internalBuilder.setAttribute("messaging.destination_kind", messagingDestinationKind);
      return this;
    }

    /**
     * Sets messaging.temp_destination.
     *
     * @param messagingTempDestination A boolean that is true if the message destination is
     *     temporary.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingTempDestination(
        boolean messagingTempDestination) {
      internalBuilder.setAttribute("messaging.temp_destination", messagingTempDestination);
      return this;
    }

    /**
     * Sets messaging.protocol.
     *
     * @param messagingProtocol The name of the transport protocol.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingProtocol(String messagingProtocol) {
      internalBuilder.setAttribute("messaging.protocol", messagingProtocol);
      return this;
    }

    /**
     * Sets messaging.protocol_version.
     *
     * @param messagingProtocolVersion The version of the transport protocol.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingProtocolVersion(
        String messagingProtocolVersion) {
      internalBuilder.setAttribute("messaging.protocol_version", messagingProtocolVersion);
      return this;
    }

    /**
     * Sets messaging.url.
     *
     * @param messagingUrl Connection string.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingUrl(String messagingUrl) {
      internalBuilder.setAttribute("messaging.url", messagingUrl);
      return this;
    }

    /**
     * Sets messaging.message_id.
     *
     * @param messagingMessageId A value used by the messaging system as an identifier for the
     *     message, represented as a string.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessageId(
        String messagingMessageId) {
      internalBuilder.setAttribute("messaging.message_id", messagingMessageId);
      return this;
    }

    /**
     * Sets messaging.conversation_id.
     *
     * @param messagingConversationId A value identifying the conversation to which the message
     *     belongs, represented as a string. Sometimes called "Correlation ID".
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingConversationId(
        String messagingConversationId) {
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
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessagePayloadSizeBytes(
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
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessagePayloadCompressedSizeBytes(
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
    public MessagingConsumerSynchronousSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Strongly recommended for in-process queueing systems.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets messaging.operation.
     *
     * @param messagingOperation A string identifying which part and kind of message consumption
     *     this span describes.
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingOperation(
        String messagingOperation) {
      internalBuilder.setAttribute(SemanticAttributes.MESSAGING_OPERATION, messagingOperation);
      return this;
    }
  }
}
