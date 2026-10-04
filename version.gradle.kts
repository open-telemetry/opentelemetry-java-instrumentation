val stableVersion = "3.0.0-SNAPSHOT"
val alphaVersion = "3.0.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.32.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
