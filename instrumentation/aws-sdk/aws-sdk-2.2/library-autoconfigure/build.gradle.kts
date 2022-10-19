plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  latestDepTestLibrary("software.amazon.awssdk:aws-core:+")
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:+")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:+")
  latestDepTestLibrary("software.amazon.awssdk:ec2:+")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:+")
  // TODO (trask) remove this once software.amazon.awssdk:rds:2.17.293 is fully published
  // (or our cache miss expires?)
  latestDepTestLibrary("software.amazon.awssdk:rds:2.17.292")
  latestDepTestLibrary("software.amazon.awssdk:s3:+")
  latestDepTestLibrary("software.amazon.awssdk:sqs:+")
}

tasks {
  test {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
