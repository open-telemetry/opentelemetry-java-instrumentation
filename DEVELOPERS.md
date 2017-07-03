## Developer instructions

### Testing a release locally
Run nexus locally (assuming you have docker running locally):

```bash
docker run -d -p 8081:8081 --name nexus sonatype/nexus
```

Configure the `distributionManagement` section in the root `pom.xml` to point to the repo on localhost.

Add/update your `~/.m2/settings.xml` with the following config block:

```xml
<settings>
  <servers>
    <server>
      <id>nexus-local</id>
      <username>admin</username>
      <password>admin123</password>
    </server>
  </servers>
</settings>
```

Perform the release process as described below.


### How to release a new version

#### Setup the environment (Mac-based)

```bash
brew install gpg


# Edit your m2 config (maven)
vim ~/.m2/settings.xml

<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>datadog</username>
      <password>*****</password>
    </server>
  </servers>
</settings>

# Get the password from 1password (search for 'sona' in the share-engineering vault)


# Install the gpg key
# Get the key from 1password (search for 'gpg maven key' in the share-engineering vault)

gpg --import key.asc 

# Try to sign something to test
gpg -ab dd-trace-examples/README.md

# Sometime you need to export the following prop
export GPG_TTY=$(tty) # add this to your bashrc, zshrc ...

```

#### Perform the release

1. Make sure all the project compile with the test
2. Make sure you've committed all things before releasing
3. Make sure you are on the dev branch

The project use the release maven plugin to perform the release.
The plugin push 2 commits to the repo.


Let's release!, do it in a term session (not in the IDE)
```bash

cd <PARENT_PROJET_PATH>
export GPG_TTY=$(tty

# You have to answer to some question
# -> version pattern for maven is x.y.z
# -> version pattern for scm is vx.y.z (this will be push to github)
# -> version pattern for the next release x.y.z-SNAPSHOT

# At some point, maven will ask for the gpg key password
# Get the password from 1password (search for 'gpg maven pass' in the share-engineering vault)

mvn release:clean release:prepare
mvn release:perform # This will push the release to maven

# If it's okay, you should be able to see BUILD SUCCESS
# Just way a couple of minutes (15minutes) before see the release to the maven repo

curl http://central.maven.org/maven2/com/datadoghq/
```