/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;

final class AwsJsonProtocolFactoryAccess {

  private static final OperationInfo OPERATION_INFO =
      OperationInfo.builder().hasPayloadMembers(true).httpMethod(SdkHttpMethod.POST).build();

  @Nullable private static final MethodHandle INVOKE_CREATE_PROTOCOL_MARSHALLER;

  static {
    MethodHandle invokeCreateProtocolMarshaller = null;
    try {
      Class<?> awsJsonProtocolFactoryClass =
          Class.forName("software.amazon.awssdk.protocols.json.AwsJsonProtocolFactory");
      Object awsJsonProtocolFactoryBuilder =
          awsJsonProtocolFactoryClass.getMethod("builder").invoke(null);
      awsJsonProtocolFactoryBuilder
          .getClass()
          .getMethod("clientConfiguration", SdkClientConfiguration.class)
          .invoke(
              awsJsonProtocolFactoryBuilder,
              SdkClientConfiguration.builder()
                  // AwsJsonProtocolFactory requires any URI to be present
                  .option(SdkClientOption.ENDPOINT, URI.create("http://empty"))
                  .build());
      Object awsJsonProtocolFactory =
          awsJsonProtocolFactoryBuilder
              .getClass()
              .getMethod("build")
              .invoke(awsJsonProtocolFactoryBuilder);

      MethodHandle createProtocolMarshaller =
          MethodHandles.publicLookup()
              .findVirtual(
                  awsJsonProtocolFactoryClass,
                  "createProtocolMarshaller",
                  MethodType.methodType(ProtocolMarshaller.class, OperationInfo.class));
      invokeCreateProtocolMarshaller =
          createProtocolMarshaller.bindTo(awsJsonProtocolFactory).bindTo(OPERATION_INFO);
    } catch (Throwable t) {
      // Ignore;
    }
    INVOKE_CREATE_PROTOCOL_MARSHALLER = invokeCreateProtocolMarshaller;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  static ProtocolMarshaller<SdkHttpFullRequest> createMarshaller() {
    if (INVOKE_CREATE_PROTOCOL_MARSHALLER == null) {
      return null;
    }

    try {
      return (ProtocolMarshaller<SdkHttpFullRequest>) INVOKE_CREATE_PROTOCOL_MARSHALLER.invoke();
    } catch (Throwable t) {
      return null;
    }
  }

  private AwsJsonProtocolFactoryAccess() {}
}
