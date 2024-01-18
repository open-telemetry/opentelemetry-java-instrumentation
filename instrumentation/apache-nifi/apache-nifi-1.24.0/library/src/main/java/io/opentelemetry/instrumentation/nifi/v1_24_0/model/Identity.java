package io.opentelemetry.instrumentation.nifi.v1_24_0.model;

import java.util.Objects;

import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.processor.Processor;

public class Identity implements Identifiable {

  private final String id;
  private final String name;

  public Identity(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public Identity(Connectable connectable) {
    this.id = connectable.getIdentifier();
    this.name = StandardIdentifier.getComponentName(connectable);
  }

  public Identity(Processor processor) {
    this.id = processor.getIdentifier();
    this.name = processor.getClass().getSimpleName();
  }

  protected Identity(Identity identity) {
    this.id = identity.getId();
    this.name = identity.getName();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return  name + " [" + id + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }

  @Override
  public Identity getIdentity() {
    return this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!getClass().equals(obj.getClass())) {
      return false;
    }

    Identity other = (Identity) obj;

    return Objects.equals(id, other.id);
  }
}