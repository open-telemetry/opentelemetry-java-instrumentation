plugins {
  id("otel.javaagent-testing")
}

val testServer by configurations.creating
val appLibrary by configurations.creating

configurations.named("testCompileOnly") {
  extendsFrom(appLibrary)
}

dependencies {
  appLibrary("org.springframework:spring-webmvc:3.1.0.RELEASE")
  testImplementation("javax.servlet:javax.servlet-api:3.1.0")

  val arquillianVersion = "1.7.0.Alpha10"
  implementation("org.jboss.arquillian.junit5:arquillian-junit5-container:$arquillianVersion")
  implementation("org.jboss.arquillian.protocol:arquillian-protocol-servlet:$arquillianVersion")
  testImplementation("org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6")

  testRuntimeOnly("org.wildfly.arquillian:wildfly-arquillian-container-embedded:2.2.0.Final")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webmvc-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-web-3.1:javaagent"))

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

  val copyDependencies by registering(Copy::class) {
    // test looks for spring jars that are bundled inside deployed application from this directory
    from(appLibrary).into("$buildDir/app-libs")
  }

  test {
    dependsOn(modifyLogbackJar)
    dependsOn(setupServer)
    dependsOn(copyDependencies)

    doFirst {
      // --add-modules is unrecognized on jdk8, ignore it instead of failing
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
      // needed for java 11 to avoid org.jboss.modules.ModuleNotFoundException: java.se
      jvmArgs("--add-modules=java.se")
      // add offset to default port values
      jvmArgs("-Djboss.socket.binding.port-offset=300")

      // remove logback-classic from classpath
      classpath = classpath.filter {
        !it.absolutePath.contains("logback-classic")
      }
      // add modified copy of logback-classic to classpath
      classpath = classpath.plus(files("$buildDir/tmp/logback-classic-modified.jar"))
    }
  }
}
