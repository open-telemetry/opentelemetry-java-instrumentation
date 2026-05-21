/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.remote.request.Request;
import javax.annotation.Nullable;

public class NacosRequestMapper {

  private NacosRequestMapper() {}

  public static NacosClientRequest createServerCheckRequest(String serverIp, int serverPort) {
    String peer = serverIp + ":" + serverPort;
    return new NacosClientRequest(
        new ServerCheckRequest(), "connection", "serverCheck", peer, serverIp, serverPort);
  }

  @Nullable
  public static NacosClientRequest mapClientRequest(Request request, String peer) {
    if (request instanceof InstanceRequest) {
      return createRequest(
          request, "naming", String.valueOf(((InstanceRequest) request).getType()), peer);
    }
    if (request instanceof ServiceQueryRequest) {
      return createRequest(request, "naming", "queryService", peer);
    }
    if (request instanceof SubscribeServiceRequest) {
      return createRequest(
          request,
          "naming",
          ((SubscribeServiceRequest) request).isSubscribe()
              ? "subscribeService"
              : "unsubscribeService",
          peer);
    }
    if (request instanceof ServiceListRequest) {
      return createRequest(request, "naming", "getServiceList", peer);
    }
    if (request instanceof ConfigQueryRequest) {
      return createRequest(request, "config", "queryConfig", peer);
    }
    if (request instanceof ConfigPublishRequest) {
      return createRequest(request, "config", "publishConfig", peer);
    }
    if (request instanceof ConfigRemoveRequest) {
      return createRequest(request, "config", "removeConfig", peer);
    }
    return null;
  }

  @Nullable
  public static NacosClientRequest mapServerRequest(Request request, String peer) {
    if (request instanceof NotifySubscriberRequest) {
      return createRequest(request, "naming", "notifySubscribeChange", peer);
    }
    if (request instanceof ConfigChangeNotifyRequest) {
      return createRequest(request, "config", "notifyConfigChange", peer);
    }
    return null;
  }

  private static NacosClientRequest createRequest(
      Request request, String category, String operation, String peer) {
    HostPort hostPort = HostPort.parse(peer);
    return new NacosClientRequest(
        request, category, operation, peer, hostPort.host(), hostPort.port());
  }

  private static class HostPort {
    @Nullable private final String host;
    @Nullable private final Integer port;

    private HostPort(@Nullable String host, @Nullable Integer port) {
      this.host = host;
      this.port = port;
    }

    @Nullable
    String host() {
      return host;
    }

    @Nullable
    Integer port() {
      return port;
    }

    static HostPort parse(String peer) {
      if (peer == null || peer.isEmpty()) {
        return new HostPort(null, null);
      }
      if (peer.startsWith("[") && peer.contains("]:")) {
        int separator = peer.lastIndexOf("]:");
        return new HostPort(peer.substring(1, separator), parsePort(peer.substring(separator + 2)));
      }
      int separator = peer.lastIndexOf(':');
      if (separator > 0 && peer.indexOf(':') == separator) {
        return new HostPort(peer.substring(0, separator), parsePort(peer.substring(separator + 1)));
      }
      return new HostPort(peer, null);
    }

    @Nullable
    private static Integer parsePort(String port) {
      try {
        return Integer.parseInt(port);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
  }

  private static class ServerCheckRequest extends Request {

    @Override
    public String getModule() {
      return "internal";
    }
  }
}
