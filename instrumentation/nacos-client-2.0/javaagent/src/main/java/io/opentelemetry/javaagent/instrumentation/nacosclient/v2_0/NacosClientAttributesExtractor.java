/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class NacosClientAttributesExtractor implements AttributesExtractor<NacosClientRequest, Response> {

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

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, NacosClientRequest request) {
    attributes.put(NACOS_CATEGORY, request.category());
    attributes.put(NACOS_REQUEST_TYPE, request.request().getClass().getSimpleName());

    Object rawRequest = request.request();
    if (rawRequest instanceof InstanceRequest) {
      InstanceRequest instanceRequest = (InstanceRequest) rawRequest;
      put(attributes, NACOS_NAMESPACE, instanceRequest.getNamespace());
      put(attributes, NACOS_GROUP, instanceRequest.getGroupName());
      put(attributes, NACOS_SERVICE_NAME, instanceRequest.getServiceName());
    } else if (rawRequest instanceof ServiceQueryRequest) {
      ServiceQueryRequest serviceQueryRequest = (ServiceQueryRequest) rawRequest;
      put(attributes, NACOS_NAMESPACE, serviceQueryRequest.getNamespace());
      put(attributes, NACOS_GROUP, serviceQueryRequest.getGroupName());
      put(attributes, NACOS_SERVICE_NAME, serviceQueryRequest.getServiceName());
    } else if (rawRequest instanceof SubscribeServiceRequest) {
      SubscribeServiceRequest subscribeServiceRequest = (SubscribeServiceRequest) rawRequest;
      put(attributes, NACOS_NAMESPACE, subscribeServiceRequest.getNamespace());
      put(attributes, NACOS_GROUP, subscribeServiceRequest.getGroupName());
      put(attributes, NACOS_SERVICE_NAME, subscribeServiceRequest.getServiceName());
    } else if (rawRequest instanceof ServiceListRequest) {
      ServiceListRequest serviceListRequest = (ServiceListRequest) rawRequest;
      put(attributes, NACOS_NAMESPACE, serviceListRequest.getNamespace());
      put(attributes, NACOS_GROUP, serviceListRequest.getGroupName());
      put(attributes, NACOS_SERVICE_NAME, serviceListRequest.getServiceName());
    } else if (rawRequest instanceof ConfigQueryRequest) {
      ConfigQueryRequest configQueryRequest = (ConfigQueryRequest) rawRequest;
      put(attributes, NACOS_DATA_ID, configQueryRequest.getDataId());
      put(attributes, NACOS_GROUP, configQueryRequest.getGroup());
      put(attributes, NACOS_TENANT, configQueryRequest.getTenant());
    } else if (rawRequest instanceof ConfigPublishRequest) {
      ConfigPublishRequest configPublishRequest = (ConfigPublishRequest) rawRequest;
      put(attributes, NACOS_DATA_ID, configPublishRequest.getDataId());
      put(attributes, NACOS_GROUP, configPublishRequest.getGroup());
      put(attributes, NACOS_TENANT, configPublishRequest.getTenant());
    } else if (rawRequest instanceof ConfigRemoveRequest) {
      ConfigRemoveRequest configRemoveRequest = (ConfigRemoveRequest) rawRequest;
      put(attributes, NACOS_DATA_ID, configRemoveRequest.getDataId());
      put(attributes, NACOS_GROUP, configRemoveRequest.getGroup());
      put(attributes, NACOS_TENANT, configRemoveRequest.getTenant());
    } else if (rawRequest instanceof NotifySubscriberRequest) {
      ServiceInfo serviceInfo = ((NotifySubscriberRequest) rawRequest).getServiceInfo();
      if (serviceInfo != null) {
        put(attributes, NACOS_GROUP, serviceInfo.getGroupName());
        put(attributes, NACOS_SERVICE_NAME, serviceInfo.getName());
      }
    } else if (rawRequest instanceof ConfigChangeNotifyRequest) {
      ConfigChangeNotifyRequest configChangeNotifyRequest = (ConfigChangeNotifyRequest) rawRequest;
      put(attributes, NACOS_DATA_ID, configChangeNotifyRequest.getDataId());
      put(attributes, NACOS_GROUP, configChangeNotifyRequest.getGroup());
      put(attributes, NACOS_TENANT, configChangeNotifyRequest.getTenant());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      NacosClientRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {}

  private static void put(
      AttributesBuilder attributes, AttributeKey<String> key, @Nullable String value) {
    if (value != null && !value.isEmpty()) {
      attributes.put(key, value);
    }
  }
}
