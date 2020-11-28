/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

public enum KubernetesVerb {
  GET("get"),
  LIST("list"),
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  PATCH("patch"),
  WATCH("watch"),
  DELETE_COLLECTION("deleteCollection");

  private final String value;

  KubernetesVerb(String value) {
    this.value = value;
  }

  public static KubernetesVerb of(
      String httpVerb, boolean hasNamePathParam, boolean hasWatchParam) {
    if (hasWatchParam) {
      return WATCH;
    }
    switch (httpVerb) {
      case "GET":
        if (!hasNamePathParam) {
          return LIST;
        }
        return GET;
      case "POST":
        return CREATE;
      case "PUT":
        return UPDATE;
      case "PATCH":
        return PATCH;
      case "DELETE":
        if (!hasNamePathParam) {
          return DELETE_COLLECTION;
        }
        return DELETE;
      default:
        throw new IllegalArgumentException("invalid HTTP verb for kubernetes client");
    }
  }

  public String value() {
    return value;
  }
}
