package test;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class TestForm {

  @NotNull
  @Size(min = 2, max = 30)
  private String name;

  @NotNull
  @Min(18)
  private Integer age;

  public TestForm() {}

  public TestForm(final String name, final Integer age) {
    this.name = name;
    this.age = age;
  }

  public String getName() {
    return this.name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Integer getAge() {
    return age;
  }

  public void setAge(final Integer age) {
    this.age = age;
  }

  @Override
  public String toString() {
    return "Person(Name: " + this.name + ", Age: " + this.age + ")";
  }
}
