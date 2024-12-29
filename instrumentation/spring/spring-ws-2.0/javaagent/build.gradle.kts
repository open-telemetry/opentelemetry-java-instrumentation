plugins {
  id("org.unbroken-dome.xjc")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.ws")
    module.set("spring-ws-core")
    versions.set("[2.0.0.RELEASE,]")
    // broken versions, jars don't contain classes
    skip("3.0.11.RELEASE", "3.1.0")
    assertInverse.set(true)
  }
}

sourceSets {
  test {
    resources {
      srcDirs("src/test/schema")
    }
  }
}

tasks {
  named<Checkstyle>("checkstyleTest") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

dependencies {
  compileOnly("org.springframework.ws:spring-ws-core:2.0.0.RELEASE")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.boot:spring-boot-starter-web-services:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-web:2.0.0.RELEASE")

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web-services:2.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web:2.+") // documented limitation

  testImplementation("wsdl4j:wsdl4j:1.6.3")
  testImplementation("com.sun.xml.messaging.saaj:saaj-impl:1.5.2")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
