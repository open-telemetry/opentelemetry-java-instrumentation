package io.opentelemetry.instrumentation.graphql.v17;

public class User {

  private final String name;
  private final InnerUser innerUser;

  public User(String name, InnerUser innerUser) {
    this.name = name;
    this.innerUser = innerUser;
  }
}
