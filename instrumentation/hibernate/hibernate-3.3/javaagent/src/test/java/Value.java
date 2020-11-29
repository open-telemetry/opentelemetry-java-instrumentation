/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
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
  @GeneratedValue(generator = "increment")
  @GenericGenerator(name = "increment", strategy = "increment")
  public Long getId() {
    return id;
  }

  private void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String title) {
    name = title;
  }
}
