/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository;

import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import org.springframework.data.annotation.Id;

public class Customer {

  @Id private Long id;

  private String firstName;
  private String lastName;

  protected Customer() {}

  public Customer(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.ROOT, "Customer[id=%d, firstName='%s', lastName='%s']", id, firstName, lastName);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Customer)) {
      return false;
    }
    Customer other = (Customer) obj;
    return Objects.equals(id, other.id)
        && Objects.equals(firstName, other.firstName)
        && Objects.equals(lastName, other.lastName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, firstName, lastName);
  }
}
