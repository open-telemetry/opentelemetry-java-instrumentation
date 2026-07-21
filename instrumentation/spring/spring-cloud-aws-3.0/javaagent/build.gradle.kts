plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.awspring.cloud")
    module.set("spring-cloud-aws-sqs")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.awspring.cloud:spring-cloud-aws-starter-sqs:3.0.0")
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  testInstrumentation(project(":instrumentation:aws-sdk:aws-sdk-2.2:javaagent"))

  testImplementation("org.elasticmq:elasticmq-rest-sqs_2.13")

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-web:3.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testMessagingPreview)
  }
}

if (otelProps.denyUnsafe) {
  // org.elasticmq:elasticmq-rest-sqs_2.13 uses unsafe. Future versions are likely to fix this.
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
