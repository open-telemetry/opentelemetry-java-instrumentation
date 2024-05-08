plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.shenyu")
    module.set("shenyu-web")
    versions.set("[2.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.apache.shenyu:shenyu-web:2.4.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")

  testImplementation("org.springframework.boot:spring-boot-starter-webflux:2.2.2.RELEASE") {
    exclude("org.codehaus.groovy", "groovy")
    exclude("org.springframework.boot", "spring-boot-actuator")
  }
  // based on apache shenyu 2.4.0 official example
  testImplementation("org.apache.shenyu:shenyu-spring-boot-starter-gateway:2.4.0") {
    exclude("org.codehaus.groovy", "groovy")
  }

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:2.7.+")
  latestDepTestLibrary("org.apache.shenyu:shenyu-spring-boot-starter-gateway:2.+") {
    exclude("org.codehaus.groovy", "groovy")
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.apache-shenyu.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
    force("org.springframework.boot:spring-boot-starter-actuator:2.2.2.RELEASE")
  }
}
