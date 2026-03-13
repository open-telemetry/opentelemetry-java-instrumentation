import io.opentelemetry.javaagent.muzzle.AcceptableVersions
import io.opentelemetry.javaagent.muzzle.MuzzleExtension
import org.gradle.util.internal.VersionNumber

tasks {
  val resolveLatestDepVersions by registering {
    group = "Help"
    description = "Resolve latest dependency versions and write to .github/config/latest-dep-versions.json"

    doLast {
      if (gradle.startParameter.projectProperties["testLatestDeps"] != "true" ||
        gradle.startParameter.projectProperties["resolveLatestDeps"] != "true") {
        throw GradleException("Must run with -PtestLatestDeps=true -PresolveLatestDeps=true")
      }

      val versions = sortedMapOf<String, String>()

      fun recordVersion(key: String, version: String) {
        val existing = versions[key]
        if (existing == null || VersionNumber.parse(version) > VersionNumber.parse(existing)) {
          versions[key] = version
        }
      }

      // Resolve an artifact with a stability filter, rejecting pre-release versions.
      fun resolveStableVersion(project: Project, group: String, module: String, version: String): String? {
        val detached = project.configurations.detachedConfiguration(
          project.dependencies.create("$group:$module:$version")
        )
        detached.resolutionStrategy.componentSelection.all {
          if (!AcceptableVersions.isStable(candidate.version)) {
            reject("pre-release version")
          }
        }
        return try {
          detached.incoming.resolutionResult.rootComponent.get()
            .dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .firstOrNull()
            ?.selected?.moduleVersion?.version
        } catch (e: Exception) {
          null
        }
      }

      // Returns the stable version for a resolved dependency, or null if it should be skipped.
      fun stableVersionOf(
        project: Project,
        group: String,
        module: String,
        reqVersion: String,
        selectedVersion: String
      ): String? {
        if (AcceptableVersions.isStable(selectedVersion)) return selectedVersion
        // Gradle picked a pre-release — re-resolve with stability filter
        return resolveStableVersion(project, group, module, reqVersion)
      }

      subprojects {
        configurations
          .filter { it.isCanBeResolved && it.name.contains("test", ignoreCase = true) && it.name.endsWith("RuntimeClasspath") }
          .forEach { config ->
            try {
              config.incoming.resolutionResult.allDependencies.forEach { dep ->
                if (dep is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                  val requested = dep.requested
                  if (requested is org.gradle.api.artifacts.component.ModuleComponentSelector) {
                    val reqVersion = requested.version
                    val selectedVersion = dep.selected.moduleVersion?.version ?: return@forEach
                    val version = stableVersionOf(this@subprojects, requested.group, requested.module, reqVersion, selectedVersion)
                      ?: return@forEach
                    if (reqVersion == "latest.release") {
                      recordVersion("${requested.group}:${requested.module}#+", version)
                    } else if (reqVersion.contains("+")) {
                      recordVersion("${requested.group}:${requested.module}#$reqVersion", version)
                    }
                  }
                }
              }
            } catch (e: Exception) {
              logger.warn("Failed to resolve ${this.path}:${config.name}: ${e.message}")
            }
          }
      }

      // Resolve Spring Boot catalog versions using a detached configuration.
      // The subproject scan doesn't capture these because the Spring Boot Gradle plugin
      // applies the BOM at an already-resolved version, not the original "3.+"/"4.+" range.
      listOf("3.+", "4.+").forEach { range ->
        val resolvedVersion = resolveStableVersion(project, "org.springframework.boot", "spring-boot-dependencies", range)
        if (resolvedVersion != null) {
          versions["org.springframework.boot:spring-boot-dependencies#$range"] = resolvedVersion
        }
      }

      // Resolve muzzle-only artifacts that don't appear in test classpaths.
      // muzzle pass/fail directives reference artifacts that resolveUpperBound() needs pinned,
      // but many of these are never pulled into a testLatestDeps configuration.
      val muzzleArtifacts = mutableSetOf<String>()
      subprojects {
        val muzzleExt = extensions.findByType<MuzzleExtension>()
        if (muzzleExt != null) {
          muzzleExt.directives.getOrElse(listOf()).forEach { directive ->
            if (directive.coreJdk.getOrElse(false)) return@forEach
            val group = directive.group.orNull ?: return@forEach
            val module = directive.module.orNull ?: return@forEach
            // Skip template variables like play_$scalaVersion that can't be resolved statically
            if (group.contains("\$") || module.contains("\$")) return@forEach
            muzzleArtifacts.add("$group:$module")
          }
        }
      }
      muzzleArtifacts.forEach { coords ->
        val key = "$coords#+"
        if (versions.containsKey(key)) return@forEach
        val (group, module) = coords.split(":")
        val resolvedVersion = resolveStableVersion(project, group, module, "latest.release")
        if (resolvedVersion != null) {
          recordVersion(key, resolvedVersion)
        }
      }

      val outputFile = file(".github/config/latest-dep-versions.json")
      outputFile.parentFile.mkdirs()
      outputFile.printWriter().use { writer ->
        writer.println("{")
        val entries = versions.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
          val comma = if (index < entries.size - 1) "," else ""
          writer.println("  \"$key\": \"$value\"$comma")
        }
        writer.println("}")
      }

      logger.lifecycle("Wrote ${versions.size} pinned versions to ${outputFile.path}")
    }
  }
}
