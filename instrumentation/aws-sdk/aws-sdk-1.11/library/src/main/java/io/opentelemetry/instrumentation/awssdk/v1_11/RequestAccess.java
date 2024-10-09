/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

final class RequestAccess {

  private static final ClassValue<RequestAccess> REQUEST_ACCESSORS =
      new ClassValue<RequestAccess>() {
        @Override
        protected RequestAccess computeValue(Class<?> type) {
          return new RequestAccess(type);
        }
      };

  @Nullable
  static String getLambdaName(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaName, request);
  }

  @Nullable
  static String getLambdaResourceId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getLambdaResourceId, request);
  }

  @Nullable
  static String getSecretArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSecretArn, request);
  }

  @Nullable
  static String getSnsTopicArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getSnsTopicArn, request);
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
  static String getAgentId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getAgentId, request);
  }

  @Nullable
  static String getKnowledgeBaseId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getKnowledgeBaseId, request);
  }

  @Nullable
  static String getDataSourceId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getDataSourceId, request);
  }

  @Nullable
  static String getGuardrailId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getGuardrailId, request);
  }

  @Nullable
  static String getGuardrailArn(Object request) {
    return findNestedAccessorOrNull(request, "getGuardrailArn");
  }

  @Nullable
  static String getModelId(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getModelId, request);
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
  @Nullable private final MethodHandle getStreamName;
  @Nullable private final MethodHandle getTableName;
  @Nullable private final MethodHandle getAgentId;
  @Nullable private final MethodHandle getKnowledgeBaseId;
  @Nullable private final MethodHandle getDataSourceId;
  @Nullable private final MethodHandle getGuardrailId;
  @Nullable private final MethodHandle getModelId;
  @Nullable private final MethodHandle getStateMachineArn;
  @Nullable private final MethodHandle getStepFunctionsActivityArn;
  @Nullable private final MethodHandle getSnsTopicArn;
  @Nullable private final MethodHandle getSecretArn;
  @Nullable private final MethodHandle getLambdaName;
  @Nullable private final MethodHandle getLambdaResourceId;

  private RequestAccess(Class<?> clz) {
    getBucketName = findAccessorOrNull(clz, "getBucketName");
    getQueueUrl = findAccessorOrNull(clz, "getQueueUrl");
    getQueueName = findAccessorOrNull(clz, "getQueueName");
    getStreamName = findAccessorOrNull(clz, "getStreamName");
    getTableName = findAccessorOrNull(clz, "getTableName");
    getAgentId = findAccessorOrNull(clz, "getAgentId");
    getKnowledgeBaseId = findAccessorOrNull(clz, "getKnowledgeBaseId");
    getDataSourceId = findAccessorOrNull(clz, "getDataSourceId");
    getGuardrailId = findAccessorOrNull(clz, "getGuardrailId");
    getModelId = findAccessorOrNull(clz, "getModelId");
    getStateMachineArn = findAccessorOrNull(clz, "getStateMachineArn");
    getStepFunctionsActivityArn = findAccessorOrNull(clz, "getActivityArn");
    getSnsTopicArn = findAccessorOrNull(clz, "getTopicArn");
    getSecretArn = findAccessorOrNull(clz, "getARN");
    getLambdaName = findAccessorOrNull(clz, "getFunctionName");
    getLambdaResourceId = findAccessorOrNull(clz, "getUUID");
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

  @Nullable
  private static String findNestedAccessorOrNull(Object obj, String... methodNames) {
    Object current = obj;
    for (String methodName : methodNames) {
      if (current == null) {
        return null;
      }
      try {
        Method method = current.getClass().getMethod(methodName);
        current = method.invoke(current);
      } catch (Exception e) {
        return null;
      }
    }
    return (current instanceof String) ? (String) current : null;
  }
}
