plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.cloud")
    module.set("spring-cloud-starter-gateway-server-webmvc")
    versions.set("[4.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc:4.3.0")

  implementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:javaagent"))

  testInstrumentation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:testing"))

  testLibrary("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc:5.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-test:4.0.0")

  // latest Spring Cloud release is not compatible with Spring Boot 4.1
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:4.0.+") // related dependency
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.spring-cloud-gateway.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}

// spring 7 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
