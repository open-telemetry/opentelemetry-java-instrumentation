plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-webmvc")
    versions.set("[6.0.0,)")
    // these versions depend on org.springframework:spring-web which has a bad dependency on
    // javax.faces:jsf-api:1.1 which was released as pom only
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    // 3.2.1.RELEASE has transitive dependencies like spring-web as "provided" instead of "compile"
    skip("3.2.1.RELEASE")
    extraDependency("jakarta.servlet:jakarta.servlet-api:5.0.0")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-common:javaagent"))

  compileOnly("org.springframework:spring-webmvc:6.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  // Include servlet instrumentation for verifying the tomcat requests
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-web:spring-web-6.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-common:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-web:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-security:3.0.0")
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  val testExperimental by registering(Test::class) {
    systemProperty("metaDataConfig", "otel.instrumentation.spring-webmvc.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.spring-webmvc.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
