plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-extension-aws")

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  testImplementation("org.assertj:assertj-core")
  testImplementation("org.mockito:mockito-core")

  latestDepTestLibrary("software.amazon.awssdk:kinesis:+")
}

tasks {
  test {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
