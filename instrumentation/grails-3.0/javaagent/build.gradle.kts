plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.grails")
    module.set("grails-web-url-mappings")
    versions.set("[3.0,)")
    // version 3.1.15 depends on org.grails:grails-datastore-core:5.0.13.BUILD-SNAPSHOT
    // which (obviously) does not exist
    // version 3.3.6 depends on org.grails:grails-datastore-core:6.1.10.BUILD-SNAPSHOT
    // which (also obviously) does not exist
    skip("3.1.15", "3.3.6")
    // these versions pass if you add the grails maven repository (https://repo.grails.org/artifactory/core)
    skip("3.2.0", "3.3.0", "3.3.1", "3.3.2", "3.3.3", "3.3.10", "3.3.13", "3.3.14", "3.3.15", "3.3.16", "3.3.17", "3.3.18", "4.0.0", "4.0.1", "4.0.5", "4.0.6", "4.0.7", "4.0.8", "4.0.9", "4.0.10", "4.0.11", "4.0.12", "4.0.13")
    assertInverse.set(true)
  }
}

otelJava {
  maxJavaVersionSupported.set(JavaVersion.VERSION_17)
}

val grailsVersion = "3.0.6" // first version that the tests pass on
val springBootVersion = "1.2.5.RELEASE"

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.grails:grails-plugin-url-mappings:$grailsVersion")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-3.1:javaagent"))

  testLibrary("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  testLibrary("org.springframework.boot:spring-boot-starter-tomcat:$springBootVersion")

  latestDepTestLibrary("org.springframework.boot:spring-boot-autoconfigure:2.+") // related dependency
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-tomcat:2.+") // related dependency
}

// testing-common pulls in groovy 4 and spock as dependencies, exclude them
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (!latestDepTest) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          if (requested.group == "org.codehaus.groovy") {
            useVersion("3.0.9")
          }
        }
      }
    }
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", latestDepTest)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
