plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("software.amazon.awssdk")
    module.set("aws-core")
    versions.set("[2.2.0,)")
    // Used by all SDK services, the only case it isn't is an SDK extension such as a custom HTTP
    // client, which is not target of instrumentation anyways.
    extraDependency("software.amazon.awssdk:protocol-core")
    excludeInstrumentationName("aws-sdk-2.2-sqs")

    // several software.amazon.awssdk artifacts are missing for this version
    skip("2.17.200")
  }

  fail {
    group.set("software.amazon.awssdk")
    module.set("aws-core")
    versions.set("[2.2.0,)")
    // Used by all SDK services, the only case it isn't is an SDK extension such as a custom HTTP
    // client, which is not target of instrumentation anyways.
    extraDependency("software.amazon.awssdk:protocol-core")

    // "fail" asserts that *all* the instrumentation modules fail to load, but the core one is
    // actually expected to succeed, so exclude it from checks.
    excludeInstrumentationName("aws-sdk-2.2-core")

    // If we don't exclude this, Muzzle must fail due to the unresolved SQS references.
    // excludeInstrumentationName("aws-sdk-2.2-sqs")

    // several software.amazon.awssdk artifacts are missing for this version
    skip("2.17.200")
  }

  pass {
    group.set("software.amazon.awssdk")
    module.set("sqs")
    versions.set("[2.2.0,)")
    // Used by all SDK services, the only case it isn't is an SDK extension such as a custom HTTP
    // client, which is not target of instrumentation anyways.
    extraDependency("software.amazon.awssdk:protocol-core")

    // several software.amazon.awssdk artifacts are missing for this version
    skip("2.17.200")
  }
}

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library-autoconfigure"))
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:sqs:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))
  // Make sure these don't add HTTP headers
  testImplementation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testImplementation(project(":instrumentation:netty:netty-4.1:javaagent"))

  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:+")
  latestDepTestLibrary("software.amazon.awssdk:aws-core:+")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:+")
  latestDepTestLibrary("software.amazon.awssdk:ec2:+")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:+")
  latestDepTestLibrary("software.amazon.awssdk:rds:+")
  latestDepTestLibrary("software.amazon.awssdk:s3:+")
  latestDepTestLibrary("software.amazon.awssdk:sqs:+")
}

tasks {
  val testExperimentalSqs by registering(Test::class) {
    group = "verification"

    systemProperty("otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", "true")
  }

  check {
    dependsOn(testExperimentalSqs)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    // TODO run tests both with and without experimental span attributes
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", "true")
  }

  withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    mergeServiceFiles {
      include("software/amazon/awssdk/global/handlers/execution.interceptors")
    }
  }
}
