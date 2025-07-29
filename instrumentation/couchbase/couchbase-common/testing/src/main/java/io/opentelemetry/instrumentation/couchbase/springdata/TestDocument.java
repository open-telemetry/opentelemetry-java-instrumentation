/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase.springdata;

import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

@Document
public class TestDocument {
  @Id private String id = "1";
  private String data = "some data";

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof TestDocument)) {
      return false;
    }
    TestDocument doc = (TestDocument) object;
    return Objects.equals(id, doc.id) && Objects.equals(data, doc.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }
}
