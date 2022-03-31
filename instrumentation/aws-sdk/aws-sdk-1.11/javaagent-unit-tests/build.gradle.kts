plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:javaagent"))

  testImplementation("com.amazonaws:aws-java-sdk-core:1.11.0")
  testImplementation("com.amazonaws:aws-java-sdk-sqs:1.11.106")
}
