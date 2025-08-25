plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  compileOnly("com.openai:openai-java:3.0.0")
}
