plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  latestDepTestLibrary("software.amazon.awssdk:aws-core:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:ec2:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:rds:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:s3:2.17.+")
  latestDepTestLibrary("software.amazon.awssdk:sqs:2.17.+")
}

tasks {
  test {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
