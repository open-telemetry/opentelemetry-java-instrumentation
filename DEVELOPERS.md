## Developer instructions

### Testing a release locally
Run artifactory locally (assuming you have docker running locally):

```bash
docker run -p 8080:8080 mattgruter/artifactory
```

Configure the `contextUrl` section in `publish.gradle` to point to `http://localhost:8080/artifactory`.

Add/update your `~/.gradle/gradle.properties` with the following config block:

```properties
forceLocal=true
bintrayUser=admin
bintrayApiKey=password
```

Upload artifacts to the local artifactory:

```bash
./gradlew artifactoryPublish
```

## Interim method to publish to Maven Central

### Publish to Bintray and Sync with Maven Central

Prerequisites:
* A bintray account that is a member of the [Datadog org](https://bintray.com/datadog)
* The bintray api key for your [account profile](https://bintray.com/profile/edit)
* Sonatype credentials

Prepare the release:
1. Checkout master: `git checkout master ; git pull --rebase`
2. Update the version number in `dd-trace-java.gradle`, removing the `-SNAPSHOT`
3. Commit with a message `Version x.x.x` (but don't push)
4. Tag the commit with tag `vx.x.x` (but don't push)
5. Ensure the build is clean: `./gradlew clean check --parallel`

Perform the release:
1. `./gradlew bintrayUpload --max-workers=1 -PbintrayUser=<YOUR_USERNAME> -PbintrayApiKey=<YOUR_API_KEY>`
2. If successful, go to [Bintray](https://bintray.com/datadog/datadog-maven/dd-trace-java) to verify artifacts.
3. After artifacts are determined correct, sync with Sonatype.
4. Enter Sonatype's credentials and press sync (leave "Close and Release" checked).
5. After a few minutes, sync status should show success. Verify the new version shows up in [Sonatype](https://oss.sonatype.org/content/repositories/releases/com/datadoghq/).
6. Push the release commit and tag to Github.
7. Update the version number in `dd-trace-java.gradle` with the next version adding `-SNAPSHOT`.
8. Commit with a message `Begin x.x.x` and push.
