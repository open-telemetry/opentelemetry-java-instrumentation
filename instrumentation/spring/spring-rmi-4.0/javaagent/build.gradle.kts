plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-context")
    versions.set("[4.0.0.RELEASE,6)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:rmi:bootstrap"))
  testInstrumentation(project(":instrumentation:rmi:javaagent"))

  library("org.springframework:spring-context:4.0.0.RELEASE")
  library("org.springframework:spring-aop:4.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot:1.1.0.RELEASE")
  testImplementation("org.apache.tomee:openejb-core:8.0.16")

  // rmi remoting was removed in spring 6
  latestDepTestLibrary("org.springframework:spring-context:5.+") // documented limitation
  latestDepTestLibrary("org.springframework:spring-aop:5.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot:2.+") // documented limitation
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Djava.rmi.server.hostname=127.0.0.1")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
