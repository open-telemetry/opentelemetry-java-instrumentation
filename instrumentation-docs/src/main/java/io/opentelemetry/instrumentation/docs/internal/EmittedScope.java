/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Representation of Scopes emitted the tests in a module. This class is internal and is hence not
 * for public use. Its APIs are unstable and can change at any time.
 */
public class EmittedScope {
  @Nullable private List<Scope> scopes;

  public EmittedScope() {}

  public EmittedScope(List<Scope> scopes) {
    this.scopes = scopes;
  }

  @Nullable
  public List<Scope> getScopes() {
    return scopes;
  }

  public void setScopes(List<Scope> scopes) {
    this.scopes = scopes;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Scope {
    @Nullable private String name;
    @Nullable private String version;
    @Nullable private String schemaUrl;
    @Nullable private Map<String, Object> attributes;

    public Scope() {}

    public Scope(String name, String version, String schemaUrl, Map<String, Object> attributes) {
      this.name = name;
      this.version = version;
      this.schemaUrl = schemaUrl;
      this.attributes = attributes;
    }

    @Nullable
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @Nullable
    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    @Nullable
    public String getSchemaUrl() {
      return schemaUrl;
    }

    public void setSchemaUrl(String schemaUrl) {
      this.schemaUrl = schemaUrl;
    }

    @Nullable
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Scope scope)) {
        return false;
      }

      if (!Objects.equals(name, scope.name)) {
        return false;
      }
      if (!Objects.equals(version, scope.version)) {
        return false;
      }
      if (!Objects.equals(schemaUrl, scope.schemaUrl)) {
        return false;
      }
      return Objects.equals(attributes, scope.attributes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version, schemaUrl, attributes);
    }
  }
}
