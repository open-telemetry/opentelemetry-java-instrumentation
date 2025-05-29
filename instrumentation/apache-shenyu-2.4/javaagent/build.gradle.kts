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
  library("org.apache.shenyu:shenyu-web:2.4.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")

  // based on apache shenyu 2.4.0 official example
  testLibrary("org.apache.shenyu:shenyu-spring-boot-starter-gateway:2.4.0") {
    exclude("org.codehaus.groovy", "groovy")
  }
  testImplementation("org.springframework.boot:spring-boot-starter-webflux:2.2.2.RELEASE") {
    exclude("org.codehaus.groovy", "groovy")
  }

  // the latest version of apache shenyu uses spring-boot 3.3
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:3.3.+") // related dependency

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 (spring boot 3) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.apache-shenyu.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  systemProperty("testLatestDeps", latestDepTest)
}

// spring 6 (spring boot 3) uses slf4j 2.0
if (!latestDepTest) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
