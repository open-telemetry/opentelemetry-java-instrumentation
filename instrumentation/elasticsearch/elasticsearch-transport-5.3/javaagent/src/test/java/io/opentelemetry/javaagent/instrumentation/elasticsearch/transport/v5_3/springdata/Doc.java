/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Document(indexName = "test-index")
class Doc {
  @Id private String id = "1";
  private String data = "some data";

  public Doc() {}

  public Doc(int id, String data) {
    this(String.valueOf(id), data);
  }

  public Doc(String id, String data) {
    this.id = id;
    this.data = data;
  }

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
    if (!(object instanceof Doc)) {
      return false;
    }
    Doc doc = (Doc) object;
    return Objects.equals(id, doc.id) && Objects.equals(data, doc.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }
}
