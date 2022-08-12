import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
  id("io.github.gradle-nexus.publish-plugin")
  id("otel.spotless-conventions")
}

apply(from = "version.gradle.kts")

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))

  transitionCheckOptions {
    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    maxRetries.set(300)
    delayBetween.set(Duration.ofSeconds(10))
  }
}

description = "OpenTelemetry instrumentations for Java"

val quarkusDeployment by configurations.creating
dependencies {
quarkusDeployment("io.quarkus:quarkus-vertx-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-core-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-arc-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-common-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-smallrye-context-propagation-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-netty-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-mutiny-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-vertx-http-deployment:2.8.0.Final")
quarkusDeployment("io.quarkus:quarkus-resteasy-server-common-deployment:2.8.0.Final")
}
typealias PrintWriter = java.io.PrintWriter
typealias FileWriter = java.io.FileWriter
tasks.register("listQuarkusDependencies") {
    val writer = PrintWriter(FileWriter("/tmp/11349829280919797225.txt"))
    quarkusDeployment.files.forEach { it -> writer.println(it) }
    val componentIds = quarkusDeployment.incoming.resolutionResult.allDependencies.map { (it as ResolvedDependencyResult).selected.id }
    val result = dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class, SourcesArtifact::class)
        .execute()
    result.resolvedComponents.forEach { component ->
        val sources = component.getArtifacts(SourcesArtifact::class)
        sources.forEach { ar ->
            if (ar is ResolvedArtifactResult) {
                writer.println(ar.file)
            }
        }
    }
    writer.close()
}