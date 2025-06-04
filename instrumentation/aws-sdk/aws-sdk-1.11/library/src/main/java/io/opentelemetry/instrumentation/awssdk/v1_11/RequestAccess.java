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
  private static final String LAMBDA_REQUEST_CLASS_PREFIX = "com.amazonaws.services.lambda.model.";
  private static final String SECRETS_MANAGER_REQUEST_CLASS_PREFIX =
      "com.amazonaws.services.secretsmanager.model.";

  private static final ClassValue<RequestAccess> REQUEST_ACCESSORS =
      new ClassValue<RequestAccess>() {
        @Override
        protected RequestAccess computeValue(Class<?> type) {
          return new RequestAccess(type);
        }
      };

  @Nullable
  static String getLambdaName(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaName, request);
  }

  @Nullable
  static String getLambdaResourceId(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaResourceId, request);
  }

  @Nullable
  static String getSecretArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSecretArn, request);
  }

  @Nullable
  static String getStepFunctionsActivityArn(Object request) {
    if (request == null) {
      return null;
    }
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getStepFunctionsActivityArn, request);
  }

  @Nullable
  static String getStateMachineArn(Object request) {
    if (request == null) {
      return null;
    }
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
  static String getTopicArn(Object request) {
    if (request == null) {
      return null;
    }
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

  @Nullable private MethodHandle getBucketName;
  @Nullable private MethodHandle getQueueUrl;
  @Nullable private MethodHandle getQueueName;
  @Nullable private MethodHandle getStreamName;
  @Nullable private MethodHandle getTableName;
  @Nullable private MethodHandle getTopicArn;
  @Nullable private MethodHandle getTargetArn;
  @Nullable private MethodHandle getStateMachineArn;
  @Nullable private MethodHandle getStepFunctionsActivityArn;
  @Nullable private MethodHandle getSecretArn;
  @Nullable private MethodHandle getLambdaName;
  @Nullable private MethodHandle getLambdaResourceId;

  private RequestAccess(Class<?> clz) {
    if (clz == null) {
      return;
    }
    getBucketName = findAccessorOrNull(clz, "getBucketName");
    getQueueUrl = findAccessorOrNull(clz, "getQueueUrl");
    getQueueName = findAccessorOrNull(clz, "getQueueName");
    getStreamName = findAccessorOrNull(clz, "getStreamName");
    getTableName = findAccessorOrNull(clz, "getTableName");
    getTopicArn = findAccessorOrNull(clz, "getTopicArn");
    getTargetArn = findAccessorOrNull(clz, "getTargetArn");
    getStateMachineArn = findAccessorOrNull(clz, "getStateMachineArn");
    getStepFunctionsActivityArn = findAccessorOrNull(clz, "getActivityArn");
    String className = clz.getName();
    if (className.startsWith(SECRETS_MANAGER_REQUEST_CLASS_PREFIX)) {
      getSecretArn = findAccessorOrNull(clz, "getARN");
    }
    if (className.startsWith(LAMBDA_REQUEST_CLASS_PREFIX)) {
      getLambdaName = findAccessorOrNull(clz, "getFunctionName");
      getLambdaResourceId = findAccessorOrNull(clz, "getUUID");
    }
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
