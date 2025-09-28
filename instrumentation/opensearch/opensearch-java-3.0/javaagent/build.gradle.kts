plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.opensearch.client")
    module.set("opensearch-java")
    versions.set("[3.0,)")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("org.opensearch.client:opensearch-java:3.0.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("org.opensearch.client:opensearch-rest-client:3.0.0")
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common:testing"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))

  // For testing AwsSdk2Transport
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testImplementation("software.amazon.awssdk:auth:2.22.0")
  testImplementation("software.amazon.awssdk:identity-spi:2.22.0")
  testImplementation("software.amazon.awssdk:apache-client:2.22.0")
  testImplementation("software.amazon.awssdk:netty-nio-client:2.22.0")
  testImplementation("software.amazon.awssdk:regions:2.22.0")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
