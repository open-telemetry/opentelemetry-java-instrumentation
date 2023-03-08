/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import org.apache.pulsar.client.api.Message;

public final class PulsarRequest {
  private final Message<?> message;
  private final String destination;
  private final UrlData urlData;

  private PulsarRequest(Message<?> message, String destination, UrlData urlData) {
    this.message = message;
    this.destination = destination;
    this.urlData = urlData;
  }

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  public Message<?> getMessage() {
    return message;
  }

  public String getDestination() {
    return destination;
  }

  public UrlData getUrlData() {
    return urlData;
  }

  private static UrlData parseUrl(String url) {
    if (url == null) {
      return null;
    }

    int protocolEnd = url.indexOf("://");
    if (protocolEnd == -1) {
      return null;
    }
    int authorityStart = protocolEnd + 3;
    int authorityEnd = url.indexOf('/', authorityStart);
    if (authorityEnd == -1) {
      authorityEnd = url.length();
    }
    String authority = url.substring(authorityStart, authorityEnd);
    int portStart = authority.indexOf(':');

    String host;
    Integer port;
    if (portStart == -1) {
      host = authority;
      port = null;
    } else {
      host = authority.substring(0, portStart);
      port = Integer.parseInt(authority.substring(portStart + 1));
    }

    return new UrlData(host, port);
  }

  static class UrlData {
    final String host;
    final Integer port;

    UrlData(String host, Integer port) {
      this.host = host;
      this.port = port;
    }

    String getHost() {
      return host;
    }

    Integer getPort() {
      return port;
    }
  }
}
