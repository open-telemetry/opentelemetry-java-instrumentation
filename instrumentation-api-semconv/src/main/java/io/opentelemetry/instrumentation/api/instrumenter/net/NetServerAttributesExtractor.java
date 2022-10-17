/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a>. It is common to have access to {@link java.net.InetSocketAddress}, in which case
 * it is more convenient to use {@link InetSocketAddressNetServerAttributesGetter}.
 */
public final class NetServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final Logger logger =
      Logger.getLogger(NetServerAttributesExtractor.class.getName());

  private final NetServerAttributesGetter<REQUEST> getter;
  private final BiFunction<REQUEST, String, List<String>> requestHeaderGetter;

  public static <REQUEST, RESPONSE> NetServerAttributesExtractor<REQUEST, RESPONSE> create(
      NetServerAttributesGetter<REQUEST> getter) {
    return new NetServerAttributesExtractor<>(getter, (request, headerName) -> emptyList());
  }

  public static <REQUEST, RESPONSE> NetServerAttributesExtractor<REQUEST, RESPONSE> create(
      NetServerAttributesGetter<REQUEST> getter,
      BiFunction<REQUEST, String, List<String>> requestHeaderGetter) {
    return new NetServerAttributesExtractor<>(getter, requestHeaderGetter);
  }

  private NetServerAttributesExtractor(
      NetServerAttributesGetter<REQUEST> getter,
      BiFunction<REQUEST, String, List<String>> requestHeaderGetter) {
    this.getter = getter;
    this.requestHeaderGetter = requestHeaderGetter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request));

    boolean setSockFamily = false;

    String sockPeerAddr = getter.sockPeerAddr(request);
    if (sockPeerAddr != null) {
      setSockFamily = true;

      internalSet(attributes, NetAttributes.NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer sockPeerPort = getter.sockPeerPort(request);
      if (sockPeerPort != null && sockPeerPort > 0) {
        internalSet(attributes, NetAttributes.NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }
    }

    String hostName = getter.hostName(request);
    Integer hostPort = getter.hostPort(request);

    String hostHeader = firstHeaderValue(requestHeaderGetter.apply(request, "host"));
    int hostHeaderSeparator = -1;
    if (hostHeader != null) {
      hostHeaderSeparator = hostHeader.indexOf(':');
    }
    if (hostName == null && hostHeader != null) {
      hostName =
          hostHeaderSeparator == -1 ? hostHeader : hostHeader.substring(0, hostHeaderSeparator);
    }
    if (hostPort == null && hostHeader != null && hostHeaderSeparator != -1) {
      try {
        hostPort = Integer.parseInt(hostHeader.substring(hostHeaderSeparator + 1));
      } catch (NumberFormatException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }

    if (hostName != null) {
      internalSet(attributes, SemanticAttributes.NET_HOST_NAME, hostName);

      if (hostPort != null && hostPort > 0) {
        internalSet(attributes, SemanticAttributes.NET_HOST_PORT, (long) hostPort);
      }
    }

    String sockHostAddr = getter.sockHostAddr(request);
    if (sockHostAddr != null && !sockHostAddr.equals(hostName)) {
      setSockFamily = true;

      internalSet(attributes, NetAttributes.NET_SOCK_HOST_ADDR, sockHostAddr);

      Integer sockHostPort = getter.sockHostPort(request);
      if (sockHostPort != null && sockHostPort > 0 && !sockHostPort.equals(hostPort)) {
        internalSet(attributes, NetAttributes.NET_SOCK_HOST_PORT, (long) sockHostPort);
      }
    }

    if (setSockFamily) {
      String sockFamily = getter.sockFamily(request);
      if (sockFamily != null && !NetAttributes.SOCK_FAMILY_INET.equals(sockFamily)) {
        internalSet(attributes, NetAttributes.NET_SOCK_FAMILY, sockFamily);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  @Nullable
  static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }
}
