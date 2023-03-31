plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  latestDepTestLibrary("software.amazon.awssdk:aws-core:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:ec2:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:rds:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:s3:2.20.33")
  latestDepTestLibrary("software.amazon.awssdk:sqs:2.20.33")
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
