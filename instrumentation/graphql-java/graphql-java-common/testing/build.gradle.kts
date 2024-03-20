plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))
  implementation("com.graphql-java:graphql-java:12.0")
}
