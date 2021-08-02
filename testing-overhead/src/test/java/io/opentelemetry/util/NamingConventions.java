package io.opentelemetry.util;

public class NamingConventions {

  public final NamingConvention container = new NamingConvention("/results");
  public final NamingConvention local = new NamingConvention(".");

  public String localResults() {
    return local.root();
  }

  public String containerResults() {
    return container.root();
  }
}
