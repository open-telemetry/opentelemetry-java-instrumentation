/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import io.opentelemetry.api.common.Attributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EmittedScope {
  private Scope scope;

  public EmittedScope() {}

  public EmittedScope(Scope scope) {
    this.scope = scope;
  }

  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Scope {
    private String name;
    private String version;
    private String schemaUrl;
    private Attributes attributes;

    public Scope() {}

    public Scope(String name, String version, String schemaUrl, Attributes attributes) {
      this.name = name;
      this.version = version;
      this.schemaUrl = schemaUrl;
      this.attributes = attributes;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public String getSchemaUrl() {
      return schemaUrl;
    }

    public void setSchemaUrl(String schemaUrl) {
      this.schemaUrl = schemaUrl;
    }

    public Attributes getAttributes() {
      return attributes;
    }

    public void setAttributes(Attributes attributes) {
      this.attributes = attributes;
    }
  }
}
