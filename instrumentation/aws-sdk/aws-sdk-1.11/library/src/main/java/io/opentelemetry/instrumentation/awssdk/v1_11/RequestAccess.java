/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

final class RequestAccess {
  private static final String SECRETS_MANAGER_REQUEST_CLASS_PREFIX =
      "com.amazonaws.services.secretsmanager.model.";
  private static final String STEP_FUNCTIONS_REQUEST_CLASS_PREFIX =
      "com.amazonaws.services.stepfunctions.model.";

  private static final ClassValue<RequestAccess> REQUEST_ACCESSORS =
      new ClassValue<RequestAccess>() {
        @Override
        protected RequestAccess computeValue(Class<?> type) {
          return new RequestAccess(type);
        }
      };

  @Nullable
  static String getSecretArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSecretArn, request);
  }

  @Nullable
  static String getStepFunctionsActivityArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStepFunctionsActivityArn, request);
  }

  @Nullable
  static String getStateMachineArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStateMachineArn, request);
  }

  @Nullable
  static String getBucketName(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getBucketName, request);
  }

  @Nullable
  static String getQueueUrl(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getQueueUrl, request);
  }

  @Nullable
  static String getQueueName(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getQueueName, request);
  }

  @Nullable
  static String getStreamName(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStreamName, request);
  }

  @Nullable
  static String getTableName(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTableName, request);
  }

  @Nullable
  static String getSnsTopicArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTopicArn, request);
  }

  @Nullable
  static String getTargetArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTargetArn, request);
  }

  @Nullable
  private static String invokeOrNull(@Nullable MethodHandle method, Object obj) {
    if (method == null) {
      return null;
    }
    try {
      return (String) method.invoke(obj);
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable private final MethodHandle getBucketName;
  @Nullable private final MethodHandle getQueueUrl;
  @Nullable private final MethodHandle getQueueName;
  @Nullable private final MethodHandle getSecretArn;
  @Nullable private final MethodHandle getStreamName;
  @Nullable private final MethodHandle getTableName;
  @Nullable private final MethodHandle getTopicArn;
  @Nullable private final MethodHandle getTargetArn;
  @Nullable private final MethodHandle getStateMachineArn;
  @Nullable private final MethodHandle getStepFunctionsActivityArn;

  private RequestAccess(Class<?> clz) {
    getBucketName = findAccessorOrNull(clz, "getBucketName");
    getQueueUrl = findAccessorOrNull(clz, "getQueueUrl");
    getQueueName = findAccessorOrNull(clz, "getQueueName");
    getStreamName = findAccessorOrNull(clz, "getStreamName");
    getTableName = findAccessorOrNull(clz, "getTableName");
    getTopicArn = findAccessorOrNull(clz, "getTopicArn");
    getTargetArn = findAccessorOrNull(clz, "getTargetArn");

    boolean isSecretsManager = clz.getName().startsWith(SECRETS_MANAGER_REQUEST_CLASS_PREFIX);
    getSecretArn = isSecretsManager ? findAccessorOrNull(clz, "getARN") : null;
    boolean isStepFunction = clz.getName().startsWith(STEP_FUNCTIONS_REQUEST_CLASS_PREFIX);
    getStateMachineArn = isStepFunction ? findAccessorOrNull(clz, "getStateMachineArn") : null;
    getStepFunctionsActivityArn = isStepFunction ? findAccessorOrNull(clz, "getActivityArn") : null;
  }

  @Nullable
  private static MethodHandle findAccessorOrNull(Class<?> clz, String methodName) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(clz, methodName, MethodType.methodType(String.class));
    } catch (Throwable t) {
      return null;
    }
  }
}
