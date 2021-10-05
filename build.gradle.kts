import nebula.plugin.release.git.opinion.Strategies
import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
  id("io.github.gradle-nexus.publish-plugin")
  id("nebula.release")
  id("otel.spotless-conventions")
}

release {
  defaultVersionStrategy = Strategies.getSNAPSHOT()
}

nebulaRelease {
  addReleaseBranchPattern("""v\d+\.\d+\.x""")
}

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

// Enable after verifying Maven Central publishing once through manual closing
// tasks.release.finalizedBy tasks.closeAndReleaseRepository

description = "OpenTelemetry instrumentations for Java"
