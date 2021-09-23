plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

// compiling against 1.11.0, but instrumentation should work against 1.10.33 with varying effects,
// depending on the version's implementation. (i.e. DeleteOptionGroup may have less handlerCounts than
// expected in 1.11.84. Testing against 1.11.0 instead of 1.10.33 because the RequestHandler class
// used in testing is abstract in 1.10.33
// keeping base test version on 1.11.0 because RequestHandler2 is abstract in 1.10.33,
// therefore keeping base version as 1.11.0 even though the instrumentation probably
// is able to support up to 1.10.33
muzzle {
  pass {
    group.set("com.amazonaws")
    module.set("aws-java-sdk-core")
    versions.set("[1.10.33,)")
    assertInverse.set(true)
  }
}

testSets {
  // Features used in test_1_11_106 (builder) is available since 1.11.84, but
  // using 1.11.106 because of previous concerns with byte code differences
  // in 1.11.106, also, the DeleteOptionGroup request generates more spans
  // in 1.11.106 than 1.11.84.
  // We test older version in separate test set to test newer version and latest deps in the 'default'
  // test dir. Otherwise we get strange warnings in Idea.
  create("test_before_1_11_106")

  // We test SQS separately since we have special logic for it and want to make sure the presence of
  // SQS on the classpath doesn't conflict with tests for usage of the core SDK. This only affects
  // the agent.
  create("testSqs")
}

configurations {
  named("test_before_1_11_106RuntimeClasspath") {
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-s3:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-rds:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-ec2:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-kinesis:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-sqs:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-sns:1.11.0")
    resolutionStrategy.force("com.amazonaws:aws-java-sdk-dynamodb:1.11.0")
  }
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-extension-aws")

  implementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:library"))

  library("com.amazonaws:aws-java-sdk-core:1.11.0")

  testLibrary("com.amazonaws:aws-java-sdk-s3:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-rds:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-ec2:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-kinesis:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-dynamodb:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sns:1.11.106")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))

  add("testSqsImplementation", "com.amazonaws:aws-java-sdk-sqs:1.11.106")

  // Include httpclient instrumentation for testing because it is a dependency for aws-sdk.
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))

  // needed for kinesis:
  testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")

  // needed for SNS
  testImplementation("org.testcontainers:localstack")

  // needed by S3
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")

  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-s3:1.11.0")
  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-rds:1.11.0")
  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-ec2:1.11.0")
  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-kinesis:1.11.0")
  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-dynamodb:1.11.0")
  add("test_before_1_11_106Implementation", "com.amazonaws:aws-java-sdk-sns:1.11.0")
}

tasks {
  val test_before_1_11_106 by existing(Test::class) {
    filter {
      // this is needed because "test.dependsOn test_before_1_11_106", and so without this,
      // running a single test in the default test set will fail
      setFailOnNoMatchingTests(false)
    }
  }

  val testSqs by existing

  if (!(findProperty("testLatestDeps") as Boolean)) {
    named("check") {
      dependsOn(test_before_1_11_106)
      dependsOn(testSqs)
    }
  }

  named<Test>("test") {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.aws-sdk.experimental-span-attributes=true")
  }
}
