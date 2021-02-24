/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.io.IOException;
import java.io.InputStream;
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
import software.amazon.awssdk.utils.StringUtils;

class Serializer {

  private final AwsJsonProtocolFactory awsJsonProtocolFactory;

  Serializer() {
    awsJsonProtocolFactory =
        AwsJsonProtocolFactory.builder()
            .clientConfiguration(
                SdkClientConfiguration.builder()
                    // AwsJsonProtocolFactory requires any URI to be present
                    .option(SdkClientOption.ENDPOINT, URI.create("http://empty"))
                    .build())
            .build();
  }

  @Nullable
  String serialize(Object target) {

    if (target == null) {
      return null;
    }

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

  @Nullable
  private String serialize(SdkPojo sdkPojo) {
    Optional<ContentStreamProvider> optional =
        createMarshaller().marshall(sdkPojo).contentStreamProvider();
    return optional
        .map(
            csp -> {
              try (InputStream cspIs = csp.newStream()) {
                return IoUtils.toUtf8String(cspIs);
              } catch (IOException e) {
                return null;
              }
            })
        .orElse(null);
  }

  private String serialize(Collection<Object> collection) {
    String serialized = collection.stream().map(this::serialize).collect(Collectors.joining(","));
    return (StringUtils.isEmpty(serialized) ? null : "[" + serialized + "]");
  }

  private ProtocolMarshaller<SdkHttpFullRequest> createMarshaller() {
    // apparently AWS SDK serializers can't be reused (throwing NPEs on second use)
    return awsJsonProtocolFactory.createProtocolMarshaller(
        OperationInfo.builder().hasPayloadMembers(true).httpMethod(SdkHttpMethod.POST).build());
  }
}
