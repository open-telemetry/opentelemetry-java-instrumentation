plugins {
  id("otel.javaagent-testing")
}

val testServer by configurations.creating

dependencies {
  testImplementation("javax:javaee-api:7.0")

  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-arquillian-testing"))
  testRuntimeOnly("org.wildfly.arquillian:wildfly-arquillian-container-embedded:2.2.0.Final")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.0:javaagent"))

  // wildfly version used to run tests
  testServer("org.wildfly:wildfly-dist:18.0.0.Final@zip")
}

otelJava {
  // due to security manager deprecation this test does not work on jdk 23 with default configuration
  maxJavaVersionForTests.set(JavaVersion.VERSION_22)
}

tasks {
  // extract wildfly dist, path is used from arquillian.xml
  val setupServer by registering(Copy::class) {
    inputs.files(testServer)
    from({
      zipTree(testServer.singleFile)
    })
    into(file("build/server/"))
  }

  // logback-classic contains /META-INF/services/javax.servlet.ServletContainerInitializer
  // that breaks deploy on embedded wildfly
  // create a copy of logback-classic jar that does not have this file
  val modifyLogbackJar by registering(Jar::class) {
    destinationDirectory.set(layout.buildDirectory.dir("tmp"))
    archiveFileName.set("logback-classic-modified.jar")
    exclude("/META-INF/services/javax.servlet.ServletContainerInitializer")

    val testRuntimeClasspath = configurations.named("testRuntimeClasspath")
    from({
      testRuntimeClasspath.get().filter { it.name.contains("logback-classic") }.map { zipTree(it) }
    })
  }

  test {
    dependsOn(modifyLogbackJar)
    dependsOn(setupServer)

    // --add-modules is unrecognized on jdk8, ignore it instead of failing
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    // needed for java 11 to avoid org.jboss.modules.ModuleNotFoundException: java.se
    jvmArgs("--add-modules=java.se")
    // add offset to default port values
    jvmArgs("-Djboss.socket.binding.port-offset=400")

    // remove logback-classic from classpath and add modified copy
    classpath = classpath.filter {
      !it.absolutePath.contains("logback-classic")
    }.plus(files(layout.buildDirectory.file("tmp/logback-classic-modified.jar")))
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
