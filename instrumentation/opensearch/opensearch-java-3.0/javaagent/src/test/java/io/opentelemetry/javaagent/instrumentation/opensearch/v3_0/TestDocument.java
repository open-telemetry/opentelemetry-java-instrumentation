/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

public class TestDocument {
  private String id;
  private String message;

  public TestDocument() {}

  public TestDocument(String id, String message) {
    this.id = id;
    this.message = message;
  }

  public static TestDocument create(String id, String message) {
    return new TestDocument(id, message);
  }

  public String getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }
}
