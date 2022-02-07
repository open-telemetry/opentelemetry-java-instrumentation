plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  latestDepTestLibrary("software.amazon.awssdk:kinesis:2.17.114") // issue #5259
  latestDepTestLibrary("software.amazon.awssdk:aws-core:2.17.114") // issue #5259
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:2.17.114") // issue #5259
}

tasks {
  test {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
