/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;
import software.amazon.awssdk.protocols.json.AwsJsonProtocolFactory;
import software.amazon.awssdk.utils.IoUtils;

class Serializer {

  private final ProtocolMarshaller<SdkHttpFullRequest> protocolMarshaller;

  Serializer() {
    protocolMarshaller =
        AwsJsonProtocolFactory.builder()
            .clientConfiguration(
                SdkClientConfiguration.builder()
                    .option(SdkClientOption.ENDPOINT, URI.create("http://empty"))
                    .build())
            .build()
            .createProtocolMarshaller(
                OperationInfo.builder()
                    .hasPayloadMembers(true)
                    .httpMethod(SdkHttpMethod.POST)
                    .build());
  }

  @Nullable
  private String serialize(SdkPojo sdkPojo) {
    Optional<ContentStreamProvider> optional =
        protocolMarshaller.marshall(sdkPojo).contentStreamProvider();
    return optional
        .map(
            csp -> {
              try {
                return IoUtils.toUtf8String(csp.newStream());
              } catch (IOException e) {
                return null;
              }
            })
        .orElse(null);
  }

  private String serialize(Collection<Object> collection) {
    return collection.stream().map(this::serialize).collect(Collectors.joining(","));
  }

  @Nullable
  String serialize(Object target) {

    if (target instanceof SdkPojo) {
      return serialize((SdkPojo) target);
    }
    if (target instanceof Collection) {
      return serialize((Collection<Object>) target);
    }
    if (target instanceof Map) {
      return serialize(((Map) target).keySet());
    }
    // simple type
    return target.toString();
  }
}
