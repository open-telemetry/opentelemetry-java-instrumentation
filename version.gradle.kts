val stableVersion = "2.35.0-SNAPSHOT"
val alphaVersion = "2.35.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.34.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
