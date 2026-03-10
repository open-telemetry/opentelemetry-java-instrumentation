/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v7_0;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.NamedQuery;

@Entity
@Table
@NamedQuery(name = "TestNamedQuery", query = "from Value")
public class Value {

  private Long id;
  private String name;

  public Value() {}

  public Value(String name) {
    this.name = name;
  }

  @Id
  @ValueGeneratedId
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String title) {
    name = title;
  }
}
