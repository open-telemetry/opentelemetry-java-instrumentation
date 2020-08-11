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

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

public class MessagingProducerSynchronousSpan extends DelegatingSpan
    implements MessagingProducerSynchronousSemanticConvention {

  protected MessagingProducerSynchronousSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link MessagingProducerSynchronousSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link MessagingProducerSynchronousSpan} object.
   */
  public static MessagingProducerSynchronousSpanBuilder createMessagingProducerSynchronousSpan(
      Tracer tracer, String spanName) {
    return new MessagingProducerSynchronousSpanBuilder(tracer, spanName).setKind(Span.Kind.CLIENT);
  }

  /**
   * Creates a {@link MessagingProducerSynchronousSpan} from a {@link MessagingSpan}.
   *
   * @param builder {@link MessagingSpan.MessagingSpanBuilder} to use.
   * @return a {@link MessagingProducerSynchronousSpan} object built from a {@link MessagingSpan}.
   */
  public static MessagingProducerSynchronousSpanBuilder createMessagingProducerSynchronousSpan(
      MessagingSpan.MessagingSpanBuilder builder) {
    // we accept a builder from Messaging since MessagingProducerSynchronous "extends" Messaging
    return new MessagingProducerSynchronousSpanBuilder(builder.getSpanBuilder());
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
  public MessagingProducerSynchronousSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /**
   * Sets messaging.system.
   *
   * @param messagingSystem A string identifying the messaging system.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setMessagingSystem(String messagingSystem) {
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
  public MessagingProducerSynchronousSemanticConvention setMessagingDestination(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingDestinationKind(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingTempDestination(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingProtocol(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingProtocolVersion(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingUrl(String messagingUrl) {
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
  public MessagingProducerSynchronousSemanticConvention setMessagingMessageId(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingConversationId(
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
  public MessagingProducerSynchronousSemanticConvention setMessagingMessagePayloadSizeBytes(
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
  public MessagingProducerSynchronousSemanticConvention
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
  public MessagingProducerSynchronousSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Strongly recommended for in-process queueing systems.
   */
  @Override
  public MessagingProducerSynchronousSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /** Builder class for {@link MessagingProducerSynchronousSpan}. */
  public static class MessagingProducerSynchronousSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected MessagingProducerSynchronousSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public MessagingProducerSynchronousSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public MessagingProducerSynchronousSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public MessagingProducerSynchronousSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public MessagingProducerSynchronousSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public MessagingProducerSynchronousSpan start() {
      // check for sampling relevant field here, but there are none.
      return new MessagingProducerSynchronousSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public MessagingProducerSynchronousSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public MessagingProducerSynchronousSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public MessagingProducerSynchronousSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public MessagingProducerSynchronousSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public MessagingProducerSynchronousSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

    /**
     * Sets messaging.system.
     *
     * @param messagingSystem A string identifying the messaging system.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingSystem(String messagingSystem) {
      internalBuilder.setAttribute("messaging.system", messagingSystem);
      return this;
    }

    /**
     * Sets messaging.destination.
     *
     * @param messagingDestination The message destination name. This might be equal to the span
     *     name but is required nevertheless.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingDestination(
        String messagingDestination) {
      internalBuilder.setAttribute("messaging.destination", messagingDestination);
      return this;
    }

    /**
     * Sets messaging.destination_kind.
     *
     * @param messagingDestinationKind The kind of message destination.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingDestinationKind(
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
    public MessagingProducerSynchronousSpanBuilder setMessagingTempDestination(
        boolean messagingTempDestination) {
      internalBuilder.setAttribute("messaging.temp_destination", messagingTempDestination);
      return this;
    }

    /**
     * Sets messaging.protocol.
     *
     * @param messagingProtocol The name of the transport protocol.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingProtocol(String messagingProtocol) {
      internalBuilder.setAttribute("messaging.protocol", messagingProtocol);
      return this;
    }

    /**
     * Sets messaging.protocol_version.
     *
     * @param messagingProtocolVersion The version of the transport protocol.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingProtocolVersion(
        String messagingProtocolVersion) {
      internalBuilder.setAttribute("messaging.protocol_version", messagingProtocolVersion);
      return this;
    }

    /**
     * Sets messaging.url.
     *
     * @param messagingUrl Connection string.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingUrl(String messagingUrl) {
      internalBuilder.setAttribute("messaging.url", messagingUrl);
      return this;
    }

    /**
     * Sets messaging.message_id.
     *
     * @param messagingMessageId A value used by the messaging system as an identifier for the
     *     message, represented as a string.
     */
    public MessagingProducerSynchronousSpanBuilder setMessagingMessageId(
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
    public MessagingProducerSynchronousSpanBuilder setMessagingConversationId(
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
    public MessagingProducerSynchronousSpanBuilder setMessagingMessagePayloadSizeBytes(
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
    public MessagingProducerSynchronousSpanBuilder setMessagingMessagePayloadCompressedSizeBytes(
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
    public MessagingProducerSynchronousSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Strongly recommended for in-process queueing systems.
     */
    public MessagingProducerSynchronousSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }
  }
}
