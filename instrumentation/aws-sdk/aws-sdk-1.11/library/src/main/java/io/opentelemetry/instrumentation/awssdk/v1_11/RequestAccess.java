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

  private static final ClassValue<RequestAccess> REQUEST_ACCESSORS =
      new ClassValue<RequestAccess>() {
        @Override
        protected RequestAccess computeValue(Class<?> type) {
          return new RequestAccess(type);
        }
      };

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
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTopicArn, request);
  }

  @Nullable
  static String getTargetArn(Object request) {
    RequestAccess access = REQUEST_ACCESSORS.get(request.getClass());
    return invokeOrNull(access.getTargetArn, request);
  }

  @Nullable
  static String getRegion(Object request) {
    String url = getQueueUrl(request);

    if (url == null) {
      return null;
    }

    url = url.replace("http://", "");
    url = url.replace("https://", "");

    if (url.startsWith("queue.amazonaws.com/")) {
      return "us-east-1";
    }
    if (isSqsUrl(url)) {
      return getRegionFromSqsUrl(url);
    } else if (isLegacySqsUrl(url)) {
      return getRegionFromLegacySqsUrl(url);
    } else {
      return null;
    }
  }

  private static boolean isSqsUrl(String url) {
    return url.startsWith("sqs.") && url.contains(".amazonaws.com/");
  }

  private static boolean isLegacySqsUrl(String url) {
    return url.contains(".queue.amazonaws.com/");
  }

  private static String getRegionFromSqsUrl(String url) {
    String[] split = url.split("\\.");

    if (split.length >= 2) {
      return split[1];
    }

    return null;
  }

  private static String getRegionFromLegacySqsUrl(String url) {
    String[] split = url.split("\\.");
    return split[0];
  }

  @Nullable
  static String getAccountId(Object request) {
    String url = getQueueUrl(request);

    if (url == null) {
      return null;
    }

    url = url.replace("http://", "");
    url = url.replace("https://", "");

    String[] split = url.split("/");
    if (split.length >= 2) {
      return split[1];
    }

    return null;
  }

  @Nullable
  static String getPartition(Object request) {
    String region = getRegion(request);

    if (region == null) {
      return null;
    }

    if (region.startsWith("us-gov-")) {
      return "aws-us-gov";
    } else if (region.startsWith("cn-")) {
      return "aws-cn";
    } else {
      return "aws";
    }
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
  @Nullable private final MethodHandle getTopicArn;
  @Nullable private final MethodHandle getTargetArn;

  private RequestAccess(Class<?> clz) {
    getBucketName = findAccessorOrNull(clz, "getBucketName");
    getQueueUrl = findAccessorOrNull(clz, "getQueueUrl");
    getQueueName = findAccessorOrNull(clz, "getQueueName");
    getStreamName = findAccessorOrNull(clz, "getStreamName");
    getTableName = findAccessorOrNull(clz, "getTableName");
    getTopicArn = findAccessorOrNull(clz, "getTopicArn");
    getTargetArn = findAccessorOrNull(clz, "getTargetArn");
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
