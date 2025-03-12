/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EmittedSpans {
  private List<String> spanKinds;
  private List<EmittedSpanAttribute> attributes;

  public EmittedSpans() {}

  public EmittedSpans(List<String> spanKinds, List<EmittedSpanAttribute> attributes) {
    this.spanKinds = spanKinds;
    this.attributes = attributes;
  }

  public List<String> getSpanKinds() {
    return spanKinds;
  }

  public void setSpanKinds(List<String> spanKinds) {
    this.spanKinds = spanKinds;
  }

  public List<EmittedSpanAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<EmittedSpanAttribute> attributes) {
    this.attributes = attributes;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class EmittedSpanAttribute {
    private String name;
    private String type;

    public EmittedSpanAttribute() {}

    public EmittedSpanAttribute(String name, String type) {
      this.name = name;
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }
}
