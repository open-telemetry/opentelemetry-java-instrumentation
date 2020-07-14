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
 *   <li>messaging.system: A string identifying the messaging system.
 *   <li>messaging.destination: The message destination name. This might be equal to the span name
 *       but is required nevertheless.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 *   <li>messaging.destination_kind: The kind of message destination
 *   <li>messaging.temp_destination: A boolean that is true if the message destination is temporary.
 * </ul>
 *
 * <b>Additional constraints</b>
 *
 * <p>At least one of the following must be set:
 *
 * <ul>
 *   <li>net.peer.name
 *   <li>net.peer.ip
 * </ul>
 */
public class MessagingConsumerSynchronousSpan extends DelegatingSpan
    implements MessagingConsumerSynchronousSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    MESSAGING_OPERATION,
    MESSAGING_SYSTEM,
    MESSAGING_DESTINATION,
    MESSAGING_DESTINATION_KIND,
    MESSAGING_TEMP_DESTINATION,
    MESSAGING_PROTOCOL,
    MESSAGING_PROTOCOL_VERSION,
    MESSAGING_URL,
    MESSAGING_MESSAGE_ID,
    MESSAGING_CONVERSATION_ID,
    MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
    MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES,
    NET_PEER_PORT,
    NET_TRANSPORT,
    NET_PEER_IP,
    NET_PEER_NAME,
    NET_HOST_IP,
    NET_HOST_PORT,
    NET_HOST_NAME;

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
  private static final Logger logger =
      Logger.getLogger(MessagingConsumerSynchronousSpan.class.getName());

  public final AttributeStatus status;

  protected MessagingConsumerSynchronousSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
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
    return new MessagingConsumerSynchronousSpanBuilder(tracer, spanName).setKind(Kind.SERVER);
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
    return new MessagingConsumerSynchronousSpanBuilder(
        builder.getSpanBuilder(), builder.status.getValue());
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
    if (!this.status.isSet(AttributeStatus.MESSAGING_SYSTEM)) {
      logger.warning("Wrong usage - Span missing messaging.system attribute");
    }
    if (!this.status.isSet(AttributeStatus.MESSAGING_DESTINATION)) {
      logger.warning("Wrong usage - Span missing messaging.destination attribute");
    }
    // extra constraints.
    {
      boolean flag =
          (!this.status.isSet(AttributeStatus.NET_PEER_NAME))
              || (!this.status.isSet(AttributeStatus.NET_PEER_IP));
      if (flag) {
        logger.info("Constraint not respected!");
      }
    }
    // conditional attributes
    if (!this.status.isSet(AttributeStatus.MESSAGING_DESTINATION_KIND)) {
      logger.info("WARNING! Missing messaging.destination_kind attribute!");
    }
    if (!this.status.isSet(AttributeStatus.MESSAGING_TEMP_DESTINATION)) {
      logger.info("WARNING! Missing messaging.temp_destination attribute!");
    }
  }

  /**
   * Sets messaging.operation.
   *
   * @param messagingOperation A string identifying which part and kind of message consumption this
   *     span describes..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingOperation(
      String messagingOperation) {
    status.set(AttributeStatus.MESSAGING_OPERATION);
    delegate.setAttribute("messaging.operation", messagingOperation);
    return this;
  }

  /**
   * Sets messaging.system.
   *
   * @param messagingSystem A string identifying the messaging system..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingSystem(String messagingSystem) {
    status.set(AttributeStatus.MESSAGING_SYSTEM);
    delegate.setAttribute("messaging.system", messagingSystem);
    return this;
  }

  /**
   * Sets messaging.destination.
   *
   * @param messagingDestination The message destination name. This might be equal to the span name
   *     but is required nevertheless..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingDestination(
      String messagingDestination) {
    status.set(AttributeStatus.MESSAGING_DESTINATION);
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
    status.set(AttributeStatus.MESSAGING_DESTINATION_KIND);
    delegate.setAttribute("messaging.destination_kind", messagingDestinationKind);
    return this;
  }

  /**
   * Sets messaging.temp_destination.
   *
   * @param messagingTempDestination A boolean that is true if the message destination is
   *     temporary..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingTempDestination(
      boolean messagingTempDestination) {
    status.set(AttributeStatus.MESSAGING_TEMP_DESTINATION);
    delegate.setAttribute("messaging.temp_destination", messagingTempDestination);
    return this;
  }

  /**
   * Sets messaging.protocol.
   *
   * @param messagingProtocol The name of the transport protocol..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingProtocol(
      String messagingProtocol) {
    status.set(AttributeStatus.MESSAGING_PROTOCOL);
    delegate.setAttribute("messaging.protocol", messagingProtocol);
    return this;
  }

  /**
   * Sets messaging.protocol_version.
   *
   * @param messagingProtocolVersion The version of the transport protocol..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingProtocolVersion(
      String messagingProtocolVersion) {
    status.set(AttributeStatus.MESSAGING_PROTOCOL_VERSION);
    delegate.setAttribute("messaging.protocol_version", messagingProtocolVersion);
    return this;
  }

  /**
   * Sets messaging.url.
   *
   * @param messagingUrl Connection string..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingUrl(String messagingUrl) {
    status.set(AttributeStatus.MESSAGING_URL);
    delegate.setAttribute("messaging.url", messagingUrl);
    return this;
  }

  /**
   * Sets messaging.message_id.
   *
   * @param messagingMessageId A value used by the messaging system as an identifier for the
   *     message, represented as a string..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingMessageId(
      String messagingMessageId) {
    status.set(AttributeStatus.MESSAGING_MESSAGE_ID);
    delegate.setAttribute("messaging.message_id", messagingMessageId);
    return this;
  }

  /**
   * Sets messaging.conversation_id.
   *
   * @param messagingConversationId A value identifying the conversation to which the message
   *     belongs, represented as a string. Sometimes called "Correlation ID"..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingConversationId(
      String messagingConversationId) {
    status.set(AttributeStatus.MESSAGING_CONVERSATION_ID);
    delegate.setAttribute("messaging.conversation_id", messagingConversationId);
    return this;
  }

  /**
   * Sets messaging.message_payload_size_bytes.
   *
   * @param messagingMessagePayloadSizeBytes The (uncompressed) size of the message payload in
   *     bytes. Also use this attribute if it is unknown whether the compressed or uncompressed
   *     payload size is reported..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setMessagingMessagePayloadSizeBytes(
      long messagingMessagePayloadSizeBytes) {
    status.set(AttributeStatus.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES);
    delegate.setAttribute("messaging.message_payload_size_bytes", messagingMessagePayloadSizeBytes);
    return this;
  }

  /**
   * Sets messaging.message_payload_compressed_size_bytes.
   *
   * @param messagingMessagePayloadCompressedSizeBytes The compressed size of the message payload in
   *     bytes..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention
      setMessagingMessagePayloadCompressedSizeBytes(
          long messagingMessagePayloadCompressedSizeBytes) {
    status.set(AttributeStatus.MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES);
    delegate.setAttribute(
        "messaging.message_payload_compressed_size_bytes",
        messagingMessagePayloadCompressedSizeBytes);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number..
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Strongly recommended for in-process queueing systems.
   */
  @Override
  public MessagingConsumerSynchronousSemanticConvention setNetTransport(String netTransport) {
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
  public MessagingConsumerSynchronousSemanticConvention setNetPeerIp(String netPeerIp) {
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
  public MessagingConsumerSynchronousSemanticConvention setNetPeerName(String netPeerName) {
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
  public MessagingConsumerSynchronousSemanticConvention setNetHostIp(String netHostIp) {
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
  public MessagingConsumerSynchronousSemanticConvention setNetHostPort(long netHostPort) {
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
  public MessagingConsumerSynchronousSemanticConvention setNetHostName(String netHostName) {
    status.set(AttributeStatus.NET_HOST_NAME);
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /** Builder class for {@link MessagingConsumerSynchronousSpan}. */
  public static class MessagingConsumerSynchronousSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected MessagingConsumerSynchronousSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public MessagingConsumerSynchronousSpanBuilder(Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public MessagingConsumerSynchronousSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public MessagingConsumerSynchronousSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public MessagingConsumerSynchronousSpanBuilder setKind(Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public MessagingConsumerSynchronousSpan start() {
      // check for sampling relevant field here, but there are none.
      return new MessagingConsumerSynchronousSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets messaging.operation.
     *
     * @param messagingOperation A string identifying which part and kind of message consumption
     *     this span describes..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingOperation(
        String messagingOperation) {
      status.set(AttributeStatus.MESSAGING_OPERATION);
      internalBuilder.setAttribute("messaging.operation", messagingOperation);
      return this;
    }

    /**
     * Sets messaging.system.
     *
     * @param messagingSystem A string identifying the messaging system..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingSystem(String messagingSystem) {
      status.set(AttributeStatus.MESSAGING_SYSTEM);
      internalBuilder.setAttribute("messaging.system", messagingSystem);
      return this;
    }

    /**
     * Sets messaging.destination.
     *
     * @param messagingDestination The message destination name. This might be equal to the span
     *     name but is required nevertheless..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingDestination(
        String messagingDestination) {
      status.set(AttributeStatus.MESSAGING_DESTINATION);
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
      status.set(AttributeStatus.MESSAGING_DESTINATION_KIND);
      internalBuilder.setAttribute("messaging.destination_kind", messagingDestinationKind);
      return this;
    }

    /**
     * Sets messaging.temp_destination.
     *
     * @param messagingTempDestination A boolean that is true if the message destination is
     *     temporary..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingTempDestination(
        boolean messagingTempDestination) {
      status.set(AttributeStatus.MESSAGING_TEMP_DESTINATION);
      internalBuilder.setAttribute("messaging.temp_destination", messagingTempDestination);
      return this;
    }

    /**
     * Sets messaging.protocol.
     *
     * @param messagingProtocol The name of the transport protocol..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingProtocol(String messagingProtocol) {
      status.set(AttributeStatus.MESSAGING_PROTOCOL);
      internalBuilder.setAttribute("messaging.protocol", messagingProtocol);
      return this;
    }

    /**
     * Sets messaging.protocol_version.
     *
     * @param messagingProtocolVersion The version of the transport protocol..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingProtocolVersion(
        String messagingProtocolVersion) {
      status.set(AttributeStatus.MESSAGING_PROTOCOL_VERSION);
      internalBuilder.setAttribute("messaging.protocol_version", messagingProtocolVersion);
      return this;
    }

    /**
     * Sets messaging.url.
     *
     * @param messagingUrl Connection string..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingUrl(String messagingUrl) {
      status.set(AttributeStatus.MESSAGING_URL);
      internalBuilder.setAttribute("messaging.url", messagingUrl);
      return this;
    }

    /**
     * Sets messaging.message_id.
     *
     * @param messagingMessageId A value used by the messaging system as an identifier for the
     *     message, represented as a string..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessageId(
        String messagingMessageId) {
      status.set(AttributeStatus.MESSAGING_MESSAGE_ID);
      internalBuilder.setAttribute("messaging.message_id", messagingMessageId);
      return this;
    }

    /**
     * Sets messaging.conversation_id.
     *
     * @param messagingConversationId A value identifying the conversation to which the message
     *     belongs, represented as a string. Sometimes called "Correlation ID"..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingConversationId(
        String messagingConversationId) {
      status.set(AttributeStatus.MESSAGING_CONVERSATION_ID);
      internalBuilder.setAttribute("messaging.conversation_id", messagingConversationId);
      return this;
    }

    /**
     * Sets messaging.message_payload_size_bytes.
     *
     * @param messagingMessagePayloadSizeBytes The (uncompressed) size of the message payload in
     *     bytes. Also use this attribute if it is unknown whether the compressed or uncompressed
     *     payload size is reported..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessagePayloadSizeBytes(
        long messagingMessagePayloadSizeBytes) {
      status.set(AttributeStatus.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES);
      internalBuilder.setAttribute(
          "messaging.message_payload_size_bytes", messagingMessagePayloadSizeBytes);
      return this;
    }

    /**
     * Sets messaging.message_payload_compressed_size_bytes.
     *
     * @param messagingMessagePayloadCompressedSizeBytes The compressed size of the message payload
     *     in bytes..
     */
    public MessagingConsumerSynchronousSpanBuilder setMessagingMessagePayloadCompressedSizeBytes(
        long messagingMessagePayloadCompressedSizeBytes) {
      status.set(AttributeStatus.MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES);
      internalBuilder.setAttribute(
          "messaging.message_payload_compressed_size_bytes",
          messagingMessagePayloadCompressedSizeBytes);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number..
     */
    public MessagingConsumerSynchronousSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Strongly recommended for in-process queueing systems.
     */
    public MessagingConsumerSynchronousSpanBuilder setNetTransport(String netTransport) {
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
    public MessagingConsumerSynchronousSpanBuilder setNetPeerIp(String netPeerIp) {
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below..
     */
    public MessagingConsumerSynchronousSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostIp(String netHostIp) {
      status.set(AttributeStatus.NET_HOST_IP);
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port..
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostPort(long netHostPort) {
      status.set(AttributeStatus.NET_HOST_PORT);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below..
     */
    public MessagingConsumerSynchronousSpanBuilder setNetHostName(String netHostName) {
      status.set(AttributeStatus.NET_HOST_NAME);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }
  }
}
