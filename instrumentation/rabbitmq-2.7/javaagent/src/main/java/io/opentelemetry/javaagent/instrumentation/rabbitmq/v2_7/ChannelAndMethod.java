/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Channel;
import io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.DeliveredMessages.SettledMessages;
import java.util.Map;
import javax.annotation.Nullable;

@AutoValue
public abstract class ChannelAndMethod {

  static final String PUBLISH_METHOD = "Channel.basicPublish";
  static final String ACK_METHOD = "Channel.basicAck";
  static final String NACK_METHOD = "Channel.basicNack";
  static final String REJECT_METHOD = "Channel.basicReject";

  public static ChannelAndMethod create(Channel channel, String method) {
    return new AutoValue_ChannelAndMethod(channel, method, null, null, null, null, false);
  }

  public static ChannelAndMethod createPublish(
      Channel channel, @Nullable String exchange, @Nullable String routingKey) {
    return new AutoValue_ChannelAndMethod(
        channel, PUBLISH_METHOD, exchange, routingKey, null, null, false);
  }

  public static ChannelAndMethod createSettle(
      Channel channel,
      String method,
      long deliveryTag,
      boolean multiple,
      SettledMessages settledMessages) {
    return new AutoValue_ChannelAndMethod(
        channel, method, null, null, deliveryTag, settledMessages, multiple);
  }

  abstract Channel getChannel();

  abstract String getMethod();

  /** Returns the exchange of a {@code basicPublish} call, {@code null} for any other method. */
  @Nullable
  abstract String getExchange();

  /** Returns the routing key of a {@code basicPublish} call, {@code null} for any other method. */
  @Nullable
  abstract String getRoutingKey();

  /**
   * Returns the delivery tag of a settle call ({@code basicAck}, {@code basicNack} or {@code
   * basicReject}), {@code null} for any other method.
   */
  @Nullable
  abstract Long getDeliveryTag();

  /**
   * Returns the deliveries settled by a settle call ({@code basicAck}, {@code basicNack} or {@code
   * basicReject}), {@code null} for any other method.
   */
  @Nullable
  abstract SettledMessages getSettledMessages();

  /**
   * Returns whether a settle call settles every outstanding delivery up to and including its
   * delivery tag.
   */
  abstract boolean isMultipleSettle();

  boolean isPublish() {
    return PUBLISH_METHOD.equals(getMethod());
  }

  @Nullable private Map<String, Object> headers;

  @Nullable
  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(@Nullable Map<String, Object> headers) {
    this.headers = headers;
  }
}
