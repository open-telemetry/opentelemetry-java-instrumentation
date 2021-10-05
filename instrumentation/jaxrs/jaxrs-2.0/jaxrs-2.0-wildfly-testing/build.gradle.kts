plugins {
  id("otel.javaagent-testing")
}

val testServer by configurations.creating

dependencies {
  testImplementation("javax:javaee-api:7.0")

  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-arquillian-testing"))
  testRuntimeOnly("org.wildfly.arquillian:wildfly-arquillian-container-embedded:2.2.0.Final")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.0:javaagent"))

  // wildfly version used to run tests
  testServer("org.wildfly:wildfly-dist:18.0.0.Final@zip")
}

tasks {
  // extract wildfly dist, path is used from arquillian.xml
  val setupServer by registering(Copy::class) {
    from(zipTree(testServer.singleFile))
    into(file("build/server/"))
  }

  // logback-classic contains /META-INF/services/javax.servlet.ServletContainerInitializer
  // that breaks deploy on embedded wildfly
  // create a copy of logback-classic jar that does not have this file
  val modifyLogbackJar by registering(Jar::class) {
    destinationDirectory.set(file("$buildDir/tmp"))
    archiveFileName.set("logback-classic-modified.jar")
    exclude("/META-INF/services/javax.servlet.ServletContainerInitializer")
    doFirst {
      configurations.configureEach {
        if (name.toLowerCase().endsWith("testruntimeclasspath")) {
          val logbackJar = find { it.name.contains("logback-classic") }
          from(zipTree(logbackJar))
        }
      }
    }
  }

  test {
    dependsOn(modifyLogbackJar)
    dependsOn(setupServer)

    doFirst {
      // --add-modules is unrecognized on jdk8, ignore it instead of failing
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
      // needed for java 11 to avoid org.jboss.modules.ModuleNotFoundException: java.se
      jvmArgs("--add-modules=java.se")
      // add offset to default port values
      jvmArgs("-Djboss.socket.binding.port-offset=200")

      // remove logback-classic from classpath
      classpath = classpath.filter {
        !it.absolutePath.contains("logback-classic")
      }
      // add modified copy of logback-classic to classpath
      classpath = classpath.plus(files("$buildDir/tmp/logback-classic-modified.jar"))
    }
  }
}
