package io.opentelemetry.spring.smoketest;

import org.springframework.data.annotation.Id;


public class Customer {
	@Id
	public String id;

	public String firstName;
	public String lastName;

	public Customer() {}

	public Customer(String firstName, String lastName) {
		this.firstName = firstName;
		this.lastName = lastName;
	}

  public String getFirstName() {
    return firstName;
  }

  public String getId() {
    return id;
  }

  public String getLastName() {
    return lastName;
  }
}

