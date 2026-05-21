/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.RpcClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class NacosClientInstrumentationTest {

  private static final AttributeKey<String> NACOS_CATEGORY =
      AttributeKey.stringKey("nacos.category");
  private static final AttributeKey<String> NACOS_REQUEST_TYPE =
      AttributeKey.stringKey("nacos.request.type");
  private static final AttributeKey<String> NACOS_NAMESPACE =
      AttributeKey.stringKey("nacos.namespace");
  private static final AttributeKey<String> NACOS_GROUP = AttributeKey.stringKey("nacos.group");
  private static final AttributeKey<String> NACOS_SERVICE_NAME =
      AttributeKey.stringKey("nacos.service.name");
  private static final AttributeKey<String> NACOS_DATA_ID = AttributeKey.stringKey("nacos.data.id");
  private static final AttributeKey<String> NACOS_TENANT = AttributeKey.stringKey("nacos.tenant");

  @Test
  void mapsNamingClientRequests() {
    assertRequest(
        NacosRequestMapper.mapClientRequest(
            instanceRequest("public", "DEFAULT_GROUP", "com.example.Service", "registerInstance"),
            "127.0.0.1:8848"),
        InstanceRequest.class,
        "naming",
        "registerInstance",
        "Nacos/registerInstance",
        "127.0.0.1",
        8848);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            new ServiceQueryRequest("public", "DEFAULT_GROUP", "com.example.Service"),
            "[2001:db8::1]:9848"),
        ServiceQueryRequest.class,
        "naming",
        "queryService",
        "Nacos/queryService",
        "2001:db8::1",
        9848);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            new SubscribeServiceRequest(
                "public", "DEFAULT_GROUP", "com.example.Service", "DEFAULT", true),
            "nacos.example.com"),
        SubscribeServiceRequest.class,
        "naming",
        "subscribeService",
        "Nacos/subscribeService",
        "nacos.example.com",
        null);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            new SubscribeServiceRequest(
                "public", "DEFAULT_GROUP", "com.example.Service", "DEFAULT", false),
            "nacos.example.com:not-a-port"),
        SubscribeServiceRequest.class,
        "naming",
        "unsubscribeService",
        "Nacos/unsubscribeService",
        "nacos.example.com",
        null);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            new ServiceListRequest("public", "DEFAULT_GROUP", 1, 100), "127.0.0.1:8848"),
        ServiceListRequest.class,
        "naming",
        "getServiceList",
        "Nacos/getServiceList",
        "127.0.0.1",
        8848);
  }

  @Test
  void mapsConfigClientRequests() {
    assertRequest(
        NacosRequestMapper.mapClientRequest(
            ConfigQueryRequest.build("app.yaml", "DEFAULT_GROUP", "tenant-a"), "127.0.0.1:8848"),
        ConfigQueryRequest.class,
        "config",
        "queryConfig",
        "Nacos/queryConfig",
        "127.0.0.1",
        8848);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            configPublishRequest("app.yaml", "DEFAULT_GROUP", "tenant-a", "content"),
            "127.0.0.1:8848"),
        ConfigPublishRequest.class,
        "config",
        "publishConfig",
        "Nacos/publishConfig",
        "127.0.0.1",
        8848);

    assertRequest(
        NacosRequestMapper.mapClientRequest(
            new ConfigRemoveRequest("app.yaml", "DEFAULT_GROUP", "tenant-a", null),
            "127.0.0.1:8848"),
        ConfigRemoveRequest.class,
        "config",
        "removeConfig",
        "Nacos/removeConfig",
        "127.0.0.1",
        8848);
  }

  @Test
  void mapsServerRequests() {
    ServiceInfo serviceInfo = new ServiceInfo();
    serviceInfo.setName("com.example.Service");
    serviceInfo.setGroupName("DEFAULT_GROUP");

    assertRequest(
        NacosRequestMapper.mapServerRequest(notifySubscriberRequest(serviceInfo), "127.0.0.1:9848"),
        NotifySubscriberRequest.class,
        "naming",
        "notifySubscribeChange",
        "Nacos/notifySubscribeChange",
        "127.0.0.1",
        9848);

    assertRequest(
        NacosRequestMapper.mapServerRequest(
            ConfigChangeNotifyRequest.build("app.yaml", "DEFAULT_GROUP", "tenant-a"),
            "127.0.0.1:9848"),
        ConfigChangeNotifyRequest.class,
        "config",
        "notifyConfigChange",
        "Nacos/notifyConfigChange",
        "127.0.0.1",
        9848);
  }

  @Test
  void ignoresUnsupportedRequests() {
    assertThat(NacosRequestMapper.mapClientRequest(new UnsupportedRequest(), "127.0.0.1:8848"))
        .isNull();
    assertThat(NacosRequestMapper.mapServerRequest(new UnsupportedRequest(), "127.0.0.1:8848"))
        .isNull();
  }

  @Test
  void extractsNamingAttributes() {
    InstanceRequest request =
        instanceRequest("public", "DEFAULT_GROUP", "com.example.Service", "registerInstance");

    Attributes attributes =
        extractAttributes(NacosRequestMapper.mapClientRequest(request, "nacos.example.com:8848"));

    assertThat(attributes.get(NACOS_CATEGORY)).isEqualTo("naming");
    assertThat(attributes.get(NACOS_REQUEST_TYPE)).isEqualTo("InstanceRequest");
    assertThat(attributes.get(NACOS_NAMESPACE)).isEqualTo("public");
    assertThat(attributes.get(NACOS_GROUP)).isEqualTo("DEFAULT_GROUP");
    assertThat(attributes.get(NACOS_SERVICE_NAME)).isEqualTo("com.example.Service");
  }

  @Test
  void extractsConfigAttributes() {
    ConfigPublishRequest request =
        configPublishRequest("app.yaml", "DEFAULT_GROUP", "tenant-a", "content");

    Attributes attributes =
        extractAttributes(NacosRequestMapper.mapClientRequest(request, "nacos.example.com:8848"));

    assertThat(attributes.get(NACOS_CATEGORY)).isEqualTo("config");
    assertThat(attributes.get(NACOS_REQUEST_TYPE)).isEqualTo("ConfigPublishRequest");
    assertThat(attributes.get(NACOS_DATA_ID)).isEqualTo("app.yaml");
    assertThat(attributes.get(NACOS_GROUP)).isEqualTo("DEFAULT_GROUP");
    assertThat(attributes.get(NACOS_TENANT)).isEqualTo("tenant-a");
  }

  @Test
  void extractsNotifySubscriberAttributes() {
    ServiceInfo serviceInfo = new ServiceInfo();
    serviceInfo.setName("com.example.Service");
    serviceInfo.setGroupName("DEFAULT_GROUP");

    Attributes attributes =
        extractAttributes(
            NacosRequestMapper.mapServerRequest(
                notifySubscriberRequest(serviceInfo), "127.0.0.1:9848"));

    assertThat(attributes.get(NACOS_CATEGORY)).isEqualTo("naming");
    assertThat(attributes.get(NACOS_REQUEST_TYPE)).isEqualTo("NotifySubscriberRequest");
    assertThat(attributes.get(NACOS_GROUP)).isEqualTo("DEFAULT_GROUP");
    assertThat(attributes.get(NACOS_SERVICE_NAME)).isEqualTo("com.example.Service");
  }

  @Test
  void getsRpcAttributesAndResponseErrorType() {
    NacosClientRpcAttributesGetter getter = new NacosClientRpcAttributesGetter();
    NacosClientRequest request =
        NacosRequestMapper.mapClientRequest(
            ConfigQueryRequest.build("app.yaml", "DEFAULT_GROUP", "tenant-a"), "127.0.0.1:8848");
    TestResponse failedResponse = new TestResponse();
    failedResponse.setErrorInfo(500, "failed");
    TestResponse failedResponseWithoutCode = new TestResponse();
    failedResponseWithoutCode.setErrorInfo(0, "failed");

    assertThat(getter.getSystem(request)).isEqualTo("nacos");
    assertThat(getter.getService(request)).isEqualTo("config");
    assertThat(getter.getMethod(request)).isEqualTo("queryConfig");
    assertThat(getter.getRpcMethod(request)).isEqualTo("config/queryConfig");
    assertThat(getter.getErrorType(request, failedResponse, null)).isEqualTo("500");
    assertThat(getter.getErrorType(request, failedResponseWithoutCode, null))
        .isEqualTo("response_error");
    assertThat(getter.getErrorType(request, null, new IllegalStateException("boom"))).isNull();
  }

  @Test
  void getsNetworkAttributes() {
    NacosClientNetworkAttributesGetter getter = new NacosClientNetworkAttributesGetter();
    NacosClientRequest request =
        NacosRequestMapper.mapClientRequest(
            ConfigQueryRequest.build("app.yaml", "DEFAULT_GROUP", "tenant-a"), "127.0.0.1:8848");

    assertThat(getter.getServerAddress(request)).isEqualTo("127.0.0.1");
    assertThat(getter.getServerPort(request)).isEqualTo(8848);
    assertThat(getter.getNetworkTransport(request, null)).isEqualTo("tcp");
    assertThat(getter.getNetworkProtocolName(request, null)).isEqualTo("grpc");
    assertThat(getter.getNetworkPeerAddress(request, null)).isEqualTo("127.0.0.1");
    assertThat(getter.getNetworkPeerPort(request, null)).isEqualTo(8848);
  }

  @Test
  void prefersVirtualFieldServerInfoWhenResolvingPeer() {
    RpcClient rpcClient = mock(RpcClient.class);
    RpcClientServerInfoAccessor.set(rpcClient, new RpcClient.ServerInfo("127.0.0.1", 9848));

    assertThat(RpcClientServerInfoAccessor.resolvePeer(rpcClient)).isEqualTo("127.0.0.1:9848");
  }

  private static Attributes extractAttributes(NacosClientRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    new NacosClientAttributesExtractor().onStart(attributes, Context.root(), request);
    return attributes.build();
  }

  private static InstanceRequest instanceRequest(
      String namespace, String groupName, String serviceName, String type) {
    InstanceRequest request = new InstanceRequest();
    request.setNamespace(namespace);
    request.setGroupName(groupName);
    request.setServiceName(serviceName);
    request.setType(type);
    request.setInstance(new Instance());
    return request;
  }

  private static ConfigPublishRequest configPublishRequest(
      String dataId, String group, String tenant, String content) {
    ConfigPublishRequest request = new ConfigPublishRequest();
    request.setDataId(dataId);
    request.setGroup(group);
    request.setTenant(tenant);
    request.setContent(content);
    return request;
  }

  private static NotifySubscriberRequest notifySubscriberRequest(ServiceInfo serviceInfo) {
    NotifySubscriberRequest request = new NotifySubscriberRequest();
    request.setServiceInfo(serviceInfo);
    return request;
  }

  private static void assertRequest(
      NacosClientRequest request,
      Class<? extends Request> requestClass,
      String category,
      String operation,
      String spanName,
      String peerHost,
      Integer peerPort) {
    assertThat(request).isNotNull();
    assertThat(request.request()).isInstanceOf(requestClass);
    assertThat(request.category()).isEqualTo(category);
    assertThat(request.operation()).isEqualTo(operation);
    assertThat(request.spanName()).isEqualTo(spanName);
    assertThat(request.peerHost()).isEqualTo(peerHost);
    assertThat(request.peerPort()).isEqualTo(peerPort);
  }

  private static class UnsupportedRequest extends Request {
    @Override
    public String getModule() {
      return "unsupported";
    }
  }

  private static class TestResponse extends Response {}
}
