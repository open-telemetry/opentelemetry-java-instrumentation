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

<<<<<<< HEAD
  latestDepTestLibrary("software.amazon.awssdk:aws-core:+")
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:+")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:+")
  latestDepTestLibrary("software.amazon.awssdk:ec2:+")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:+")
  latestDepTestLibrary("software.amazon.awssdk:rds:+")
  latestDepTestLibrary("software.amazon.awssdk:s3:+")
  latestDepTestLibrary("software.amazon.awssdk:sqs:+")
=======
  latestDepTestLibrary("software.amazon.awssdk:kinesis:2.17.114") // issue #5259
  latestDepTestLibrary("software.amazon.awssdk:aws-core:2.17.114") // issue #5259
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:2.17.114") // issue #5259
>>>>>>> 407e86df2bcce80c04e16a93df07b55a298d3dc6
}

tasks {
  test {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
