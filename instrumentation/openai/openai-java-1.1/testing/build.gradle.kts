plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.openai:openai-java:1.1.0")
}
