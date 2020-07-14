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

public interface MessagingSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for net.peer.ip
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  public MessagingSemanticConvention setNetPeerIp(String netPeerIp);

  /**
   * Sets a value for net.peer.name
   *
   * @param netPeerName Remote hostname or similar, see note below..
   */
  public MessagingSemanticConvention setNetPeerName(String netPeerName);

  /**
   * Sets a value for net.host.ip
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
   */
  public MessagingSemanticConvention setNetHostIp(String netHostIp);

  /**
   * Sets a value for net.host.port
   *
   * @param netHostPort Like `net.peer.port` but for the host port..
   */
  public MessagingSemanticConvention setNetHostPort(long netHostPort);

  /**
   * Sets a value for net.host.name
   *
   * @param netHostName Local hostname or similar, see note below..
   */
  public MessagingSemanticConvention setNetHostName(String netHostName);

  /**
   * Sets a value for messaging.system
   *
   * @param messagingSystem A string identifying the messaging system..
   */
  public MessagingSemanticConvention setMessagingSystem(String messagingSystem);

  /**
   * Sets a value for messaging.destination
   *
   * @param messagingDestination The message destination name. This might be equal to the span name
   *     but is required nevertheless..
   */
  public MessagingSemanticConvention setMessagingDestination(String messagingDestination);

  /**
   * Sets a value for messaging.destination_kind
   *
   * @param messagingDestinationKind The kind of message destination.
   */
  public MessagingSemanticConvention setMessagingDestinationKind(String messagingDestinationKind);

  /**
   * Sets a value for messaging.temp_destination
   *
   * @param messagingTempDestination A boolean that is true if the message destination is
   *     temporary..
   */
  public MessagingSemanticConvention setMessagingTempDestination(boolean messagingTempDestination);

  /**
   * Sets a value for messaging.protocol
   *
   * @param messagingProtocol The name of the transport protocol..
   */
  public MessagingSemanticConvention setMessagingProtocol(String messagingProtocol);

  /**
   * Sets a value for messaging.protocol_version
   *
   * @param messagingProtocolVersion The version of the transport protocol..
   */
  public MessagingSemanticConvention setMessagingProtocolVersion(String messagingProtocolVersion);

  /**
   * Sets a value for messaging.url
   *
   * @param messagingUrl Connection string..
   */
  public MessagingSemanticConvention setMessagingUrl(String messagingUrl);

  /**
   * Sets a value for messaging.message_id
   *
   * @param messagingMessageId A value used by the messaging system as an identifier for the
   *     message, represented as a string..
   */
  public MessagingSemanticConvention setMessagingMessageId(String messagingMessageId);

  /**
   * Sets a value for messaging.conversation_id
   *
   * @param messagingConversationId A value identifying the conversation to which the message
   *     belongs, represented as a string. Sometimes called "Correlation ID"..
   */
  public MessagingSemanticConvention setMessagingConversationId(String messagingConversationId);

  /**
   * Sets a value for messaging.message_payload_size_bytes
   *
   * @param messagingMessagePayloadSizeBytes The (uncompressed) size of the message payload in
   *     bytes. Also use this attribute if it is unknown whether the compressed or uncompressed
   *     payload size is reported..
   */
  public MessagingSemanticConvention setMessagingMessagePayloadSizeBytes(
      long messagingMessagePayloadSizeBytes);

  /**
   * Sets a value for messaging.message_payload_compressed_size_bytes
   *
   * @param messagingMessagePayloadCompressedSizeBytes The compressed size of the message payload in
   *     bytes..
   */
  public MessagingSemanticConvention setMessagingMessagePayloadCompressedSizeBytes(
      long messagingMessagePayloadCompressedSizeBytes);

  /**
   * Sets a value for net.peer.port
   *
   * @param netPeerPort Remote port number..
   */
  public MessagingSemanticConvention setNetPeerPort(long netPeerPort);

  /**
   * Sets a value for net.transport
   *
   * @param netTransport Strongly recommended for in-process queueing systems.
   */
  public MessagingSemanticConvention setNetTransport(String netTransport);
}
