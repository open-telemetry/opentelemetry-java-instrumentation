#!/usr/bin/env python3
"""Propose updates to smoke-test image pins from verified publications."""

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener
import zipfile


REPOSITORY = "open-telemetry/opentelemetry-java-instrumentation"
BASE_BRANCH = "main"
ADOPTION_BRANCH = "otelbot/smoke-test-image-versions"
BOT_NAME = "otelbot"
BOT_EMAIL = "197425009+otelbot@users.noreply.github.com"
TITLE = "Update smoke-test image versions"
MERGE_TITLE = "Sync smoke-test image adoption branch with main"
PR_BODY = (
    "Proposes verified smoke-test image pin updates. Exact previous and proposed "
    "versions, along with links to their verified publisher runs, are recorded in "
    "bot-authored update comments below. Review the updates and let the ordinary "
    "smoke-test checks finish before manually merging."
)
API = "https://api.github.com"
REGISTRY = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation"
MAX_CANDIDATE_SIZE = 1024 * 1024
VERSION_PATTERN = re.compile(r"[0-9]{8}\.[0-9]+")
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")
DIGEST_PATTERN = re.compile(r"sha256:[0-9a-f]{64}")


@dataclass(frozen=True)
class Family:
    workflow_name: str
    workflow_file: str
    pin_file: str
    pin_constant: str
    image_names: tuple = ()
    java_versions: tuple = ()
    servlet: bool = False


FAMILIES = {
    "fake-backend": Family(
        "Publish fake backend images for smoke tests",
        "publish-smoke-test-fake-backend-images.yml",
        "smoke-tests/src/main/java/io/opentelemetry/smoketest/ImageVersions.java",
        "FAKE_BACKEND_VERSION",
        ("smoke-test-fake-backend", "smoke-test-fake-backend-windows"),
    ),
    "servlet": Family(
        "Publish Servlet images for smoke tests",
        "publish-smoke-test-servlet-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "SERVLET_VERSION",
        servlet=True,
    ),
    "grpc": Family(
        "Publish gRPC images for smoke tests",
        "publish-smoke-test-grpc-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "GRPC_VERSION",
        ("smoke-test-grpc",),
        ("8", "11", "17", "21", "25", "27"),
    ),
    "play": Family(
        "Publish Play images for smoke tests",
        "publish-smoke-test-play-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "PLAY_VERSION",
        ("smoke-test-play",),
        ("8", "11", "17", "21", "25", "27"),
    ),
    "quarkus": Family(
        "Publish Quarkus images for smoke tests",
        "publish-smoke-test-quarkus-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "QUARKUS_VERSION",
        ("smoke-test-quarkus",),
        ("21", "25", "27"),
    ),
    "security-manager": Family(
        "Publish Security Manager test app images for smoke tests",
        "publish-smoke-test-security-manager-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "SECURITY_MANAGER_VERSION",
        ("smoke-test-security-manager",),
        ("8", "11", "17", "21", "25", "27"),
    ),
    "spring-boot": Family(
        "Publish Spring Boot images for smoke tests",
        "publish-smoke-test-spring-boot-images.yml",
        "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
        "SPRING_BOOT_VERSION",
        ("smoke-test-spring-boot",),
        ("8", "11", "17", "21", "25", "27"),
    ),
}

PIN_FILES = (
    "smoke-tests/src/main/java/io/opentelemetry/smoketest/ImageVersions.java",
    "smoke-tests/src/test/java/io/opentelemetry/smoketest/TestImageVersions.java",
)
PIN_CONSTANTS = {
    family.pin_constant: family_name
    for family_name, family in FAMILIES.items()
}
CONSTANT_PATTERN = re.compile(
    r'public static final String ([A-Z0-9_]+) = "([0-9]{8}\.[0-9]+)";'
)
class AdoptionError(Exception):
    """A candidate or update cannot be adopted safely."""


class NotFound(AdoptionError):
    """A requested GitHub resource does not exist."""


def no_window_flags():
    if sys.platform == "win32":
        return getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000)
    return 0


def run_process(args, cwd=None, env=None, input_text=None):
    result = subprocess.run(
        args,
        cwd=cwd,
        env=env,
        input=input_text,
        capture_output=True,
        text=True,
        encoding="utf-8",
        creationflags=no_window_flags(),
    )
    if result.returncode:
        raise AdoptionError(f"{Path(args[0]).name} failed with exit code {result.returncode}")
    return result.stdout


class StripCrossHostAuthorization(HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, new_url):
        redirected = super().redirect_request(
            request, file, code, message, headers, new_url
        )
        source = urlparse(request.full_url)
        target = urlparse(new_url)
        if redirected and (
            source.scheme != target.scheme
            or source.netloc != target.netloc
            or target.scheme != "https"
        ):
            for name in ("Authorization", "Cookie", "Proxy-Authorization"):
                redirected.remove_header(name)
        return redirected


class GitHub:
    def __init__(self, token, repository=REPOSITORY):
        if repository.lower() != REPOSITORY:
            raise AdoptionError(f"unsupported repository: {repository}")
        if not token:
            raise AdoptionError("GITHUB_TOKEN is required for read-only GitHub validation")
        self.token = token
        self.repository = repository
        self.opener = build_opener(StripCrossHostAuthorization())

    def request(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        request = Request(
            f"{API}/repos/{self.repository}/{path}",
            data=data,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": "Bearer " + self.token,
                "X-GitHub-Api-Version": "2022-11-28",
                "Content-Type": "application/json",
            },
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                content_type = response.headers.get("Content-Type", "").split(";", 1)[0]
                payload = response.read()
                return json.loads(payload) if "json" in content_type else payload
        except HTTPError as error:
            if error.code == 404:
                raise NotFound("GitHub resource was not found") from None
            raise AdoptionError(f"GitHub API request failed with HTTP {error.code}") from None
        except (URLError, TimeoutError) as error:
            raise AdoptionError(f"GitHub API request failed: {type(error).__name__}") from None

    def pages(self, path, key=None):
        page = 1
        values = []
        while True:
            separator = "&" if "?" in path else "?"
            response = self.request(
                "GET",
                f"{path}{separator}{urlencode({'per_page': 100, 'page': page})}",
            )
            page_values = response if key is None else response[key]
            values.extend(page_values)
            if len(page_values) < 100:
                return values
            page += 1

    def run_attempt(self, run_id, attempt):
        return self.request(
            "GET",
            f"actions/runs/{quote(str(run_id), safe='')}/attempts/{quote(str(attempt), safe='')}",
        )

    def run_jobs(self, run_id, attempt):
        path = (
            f"actions/runs/{quote(str(run_id), safe='')}/attempts/"
            f"{quote(str(attempt), safe='')}/jobs"
        )
        return self.pages(path, "jobs")

    def run_artifacts(self, run_id):
        return self.pages(f"actions/runs/{quote(str(run_id), safe='')}/artifacts", "artifacts")

    def download_artifact(self, artifact_id):
        request = Request(
            f"{API}/repos/{self.repository}/actions/artifacts/"
            f"{quote(str(artifact_id), safe='')}/zip",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": "Bearer " + self.token,
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                payload = response.read(MAX_CANDIDATE_SIZE * 3 + 1)
        except HTTPError as error:
            raise AdoptionError(f"artifact download failed with HTTP {error.code}") from None
        except (URLError, TimeoutError) as error:
            raise AdoptionError(f"artifact download failed: {type(error).__name__}") from None
        if len(payload) > MAX_CANDIDATE_SIZE * 3:
            raise AdoptionError("candidate artifact is too large")
        try:
            with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                entries = archive.infolist()
                if (
                    len(entries) != 1
                    or entries[0].filename != "publication-candidate.json"
                    or entries[0].file_size > MAX_CANDIDATE_SIZE
                ):
                    raise AdoptionError("artifact must contain only publication-candidate.json")
                return json.loads(archive.read(entries[0]))
        except (OSError, ValueError, zipfile.BadZipFile, json.JSONDecodeError):
            raise AdoptionError("candidate artifact is not a valid JSON record") from None

    def compare(self, base, head):
        return self.request(
            "GET",
            f"compare/{quote(base, safe='')}...{quote(head, safe='')}",
        )["status"]

    def main_sha(self):
        return self.request("GET", "commits/main")["sha"]

    def workflow_runs(self, family):
        workflow = FAMILIES[family].workflow_file
        path = f"actions/workflows/{quote(workflow, safe='')}/runs?branch=main&status=success"
        return self.pages(path, "workflow_runs")

    def open_adoption_prs(self):
        query = urlencode(
            {"state": "open", "head": f"open-telemetry:{ADOPTION_BRANCH}"}
        )
        return self.pages(f"pulls?{query}")

    def create_pr(self, body):
        return self.request(
            "POST",
            "pulls",
            {
                "title": TITLE,
                "body": body,
                "head": ADOPTION_BRANCH,
                "base": BASE_BRANCH,
                "draft": True,
            },
        )

    def pr_comments(self, number):
        return self.pages(f"issues/{number}/comments")

    def create_pr_comment(self, number, body):
        return self.request("POST", f"issues/{number}/comments", {"body": body})


def expected_workflow_ref(family_name):
    family = FAMILIES[family_name]
    return (
        f"{REPOSITORY}/.github/workflows/{family.workflow_file}@refs/heads/main"
    )


def publication_id(run):
    created = datetime.fromisoformat(
        (run.get("run_started_at") or run["created_at"]).replace("Z", "+00:00")
    )
    if created.tzinfo is None:
        raise AdoptionError("publisher run has no timezone")
    return (
        f"{created.astimezone(timezone.utc):%Y%m%d}."
        f"{run['id']}{int(run['run_attempt']):06d}"
    )


def publication_id_matches(run, value):
    if not isinstance(value, str) or not VERSION_PATTERN.fullmatch(value):
        return False
    tag_date, suffix = value.split(".", 1)
    if suffix != f"{run['id']}{int(run['run_attempt']):06d}":
        return False
    try:
        published_date = datetime.strptime(tag_date, "%Y%m%d").date()
        started = datetime.fromisoformat(
            (run.get("run_started_at") or run["created_at"]).replace("Z", "+00:00")
        )
        finished = datetime.fromisoformat(
            (
                run.get("completed_at")
                or run.get("updated_at")
                or run.get("run_started_at")
                or run["created_at"]
            ).replace("Z", "+00:00")
        )
    except (AttributeError, KeyError, TypeError, ValueError):
        return False
    if started.tzinfo is None or finished.tzinfo is None:
        return False
    return (
        started.astimezone(timezone.utc).date()
        <= published_date
        <= finished.astimezone(timezone.utc).date()
    )


def run_order(run):
    started = datetime.fromisoformat(
        (run.get("run_started_at") or run["created_at"]).replace("Z", "+00:00")
    )
    return started, int(run["id"]), int(run["run_attempt"])


def expected_references(family_name, tag, java_versions=None):
    family = FAMILIES[family_name]
    if family_name == "fake-backend":
        return {
            f"{REGISTRY}/{image}:{tag}" for image in family.image_names
        }
    if family.servlet:
        raise AdoptionError("servlet variants must be generated from trusted source")
    if java_versions is None:
        java_versions = family.java_versions
    return {
        f"{REGISTRY}/{family.image_names[0]}:jdk{version}-{tag}"
        for version in java_versions
    }


def validate_run_identity(run, family_name, run_id, attempt):
    family = FAMILIES[family_name]
    if (
        str(run.get("id")) != str(run_id)
        or str(run.get("run_attempt")) != str(attempt)
        or run.get("name") != family.workflow_name
        or run.get("path") != f".github/workflows/{family.workflow_file}"
        or run.get("head_branch") != BASE_BRANCH
        or run.get("event") not in ("push", "workflow_dispatch")
        or run.get("status") != "completed"
        or run.get("conclusion") != "success"
        or not SHA_PATTERN.fullmatch(run.get("head_sha", ""))
    ):
        raise AdoptionError(
            "publisher run identity, branch, attempt, or conclusion is not eligible"
        )
    expected_tag = publication_id(run)
    if not VERSION_PATTERN.fullmatch(expected_tag):
        raise AdoptionError("publisher run has an invalid publication identifier")
    return expected_tag


def verify_required_jobs(jobs, family_name):
    job_results = {
        job.get("name"): (job.get("status"), job.get("conclusion")) for job in jobs
    }
    if family_name == "fake-backend":
        required = ("prepare", "publishLinux", "publishWindows", "verify")
        if not set(required).issubset(job_results):
            raise AdoptionError("fake-backend publication is missing a required job")
    elif family_name == "servlet":
        publish_jobs = {name for name in job_results if name.startswith("publish (")}
        if (
            len(publish_jobs) != 13
            or "prepare" not in job_results
            or "verify" not in job_results
        ):
            raise AdoptionError(
                "servlet publication is missing a required matrix or verification job"
            )
        required = ("prepare", "verify", *publish_jobs)
    else:
        required = ("publish / build", "publish / verify")
        if not set(required).issubset(job_results):
            raise AdoptionError(f"{family_name} publication is missing a required job")
    for name in required:
        if job_results[name] != ("completed", "success"):
            raise AdoptionError(f"required publication job did not succeed: {name}")


def validate_candidate(candidate, run, family_name, run_id, attempt, expected_images):
    required_keys = {
        "schema_version",
        "family",
        "repository",
        "source_commit",
        "workflow",
        "run_id",
        "run_attempt",
        "publication_id",
        "images",
    }
    if not isinstance(candidate, dict) or set(candidate) != required_keys:
        raise AdoptionError("candidate has an invalid schema")
    if (
        type(candidate["schema_version"]) is not int
        or candidate["schema_version"] != 1
        or candidate["family"] != family_name
        or candidate["repository"] != REPOSITORY
        or candidate["source_commit"] != run["head_sha"]
        or candidate["workflow"] != expected_workflow_ref(family_name)
        or str(candidate["run_id"]) != str(run_id)
        or str(candidate["run_attempt"]) != str(attempt)
        or not publication_id_matches(run, candidate["publication_id"])
    ):
        raise AdoptionError("candidate provenance does not match the successful publisher run")
    if not isinstance(candidate["images"], list):
        raise AdoptionError("candidate images must be a list")
    images = {}
    for image in candidate["images"]:
        if (
            not isinstance(image, dict)
            or set(image) != {"reference", "digest"}
            or not isinstance(image["reference"], str)
            or not isinstance(image["digest"], str)
            or not DIGEST_PATTERN.fullmatch(image["digest"])
            or image["reference"] in images
        ):
            raise AdoptionError("candidate contains a malformed or duplicate image")
        images[image["reference"]] = image["digest"]
    if not expected_images or set(images) != set(expected_images):
        raise AdoptionError("candidate does not contain the complete expected image family")
    return images


def is_newer_run(old_run, new_run, compare):
    if old_run["head_sha"] != new_run["head_sha"]:
        status = compare(old_run["head_sha"], new_run["head_sha"])
        if status == "ahead":
            return True
        if status == "behind":
            return False
        if status != "identical":
            raise AdoptionError("publication source commits are not on the same trusted lineage")
    return run_order(new_run) > run_order(old_run)


def image_pin(contents, family_name):
    family = FAMILIES[family_name]
    matches = re.findall(
        rf'public static final String {re.escape(family.pin_constant)} = "([^"]+)";',
        contents[family.pin_file],
    )
    if len(matches) != 1 or not VERSION_PATTERN.fullmatch(matches[0]):
        raise AdoptionError(f"cannot identify {family.pin_constant} in the pinned source file")
    if family_name == "fake-backend" and (
        "public static final String FAKE_BACKEND_WINDOWS_VERSION = FAKE_BACKEND_VERSION;"
        not in contents[family.pin_file]
    ):
        raise AdoptionError("fake-backend Windows pin must remain an alias")
    return matches[0]


def update_pin(contents, family_name, new_version):
    if family_name not in FAMILIES or not VERSION_PATTERN.fullmatch(new_version):
        raise AdoptionError("invalid smoke-test family or version")
    family = FAMILIES[family_name]
    old_version = image_pin(contents, family_name)
    if old_version == new_version:
        return dict(contents), old_version
    updated = dict(contents)
    pattern = re.compile(
        rf'(public static final String {re.escape(family.pin_constant)} = ")[^"]+(";)'
    )
    updated[family.pin_file], count = pattern.subn(
        rf"\g<1>{new_version}\g<2>", updated[family.pin_file]
    )
    if count != 1:
        raise AdoptionError(f"cannot update {family.pin_constant}")
    return updated, old_version


def validate_bot_branch(base_files, branch_files, commits):
    if not commits:
        raise AdoptionError("adoption PR branch has no bot-managed update commits")
    for author, email, committer, committer_email, subject in commits:
        if (
            author != BOT_NAME
            or email != BOT_EMAIL
            or committer != BOT_NAME
            or committer_email != BOT_EMAIL
            or subject != TITLE
        ):
            raise AdoptionError(
                "adoption branch contains an unexpected commit; preserve it and retry manually"
            )
    base_pins = {}
    branch_pins = {}
    for path in PIN_FILES:
        base_pins[path] = {
            name: value for name, value in CONSTANT_PATTERN.findall(base_files[path])
        }
        branch_pins[path] = {
            name: value for name, value in CONSTANT_PATTERN.findall(branch_files[path])
        }
        if set(base_pins[path]) != set(branch_pins[path]):
            raise AdoptionError("adoption branch changed an unexpected image-version constant")
    normalized = dict(branch_files)
    for path in PIN_FILES:
        for name, base_value in base_pins[path].items():
            branch_value = branch_pins[path][name]
            if name not in PIN_CONSTANTS and branch_value != base_value:
                raise AdoptionError("adoption branch changed a non-managed image-version constant")
            if name in PIN_CONSTANTS and not VERSION_PATTERN.fullmatch(branch_value):
                raise AdoptionError("adoption branch contains an invalid image-version constant")
            normalized[path] = normalized[path].replace(
                f'{name} = "{branch_value}"', f'{name} = "{base_value}"'
            )
        if normalized[path] != base_files[path]:
            raise AdoptionError(
                "adoption branch contains edits beyond managed image-version constants"
            )
    if image_pin(branch_files, "fake-backend") != branch_pins[
        FAMILIES["fake-backend"].pin_file
    ]["FAKE_BACKEND_VERSION"]:
        raise AdoptionError("invalid fake-backend pin")
    return {
        family_name: image_pin(branch_files, family_name)
        for family_name in FAMILIES
        if image_pin(base_files, family_name) != image_pin(branch_files, family_name)
    }


def require_expected_remote_head(expected, actual):
    if expected != actual:
        raise AdoptionError(
            "adoption branch changed during update; no push was made, inspect the open PR and retry"
        )


def publication_comment(update):
    family, old_version, new_version, run_id, attempt, run_url = update
    marker = (
        f"<!-- smoke-test-image-adoption:{family}:{run_id}:"
        f"{attempt}:{new_version} -->"
    )
    return (
        f"{marker}\n"
        f"Verified smoke-test image publication for **{family}**.\n\n"
        f"- Previous version: `{old_version}`\n"
        f"- Proposed version: `{new_version}`\n"
        f"- Verified publication: [run {run_id}, attempt {attempt}]({run_url})"
    )


def missing_publication_comments(comments, updates):
    comments_by_body = {
        comment.get("body", ""): comment
        for comment in comments
    }
    missing = []
    for update in updates:
        body = publication_comment(update)
        if body not in comments_by_body:
            marker = body.splitlines()[0]
            matching = [
                comment
                for comment in comments
                if marker in comment.get("body", "")
            ]
            if matching:
                raise AdoptionError(
                    "publication identity already appears in a non-identical PR comment"
                )
            missing.append((update, body))
            continue
        author = (comments_by_body[body].get("user") or {}).get("login")
        if author not in (BOT_NAME, f"{BOT_NAME}[bot]"):
            raise AdoptionError(
                "publication identity already appears in a non-bot PR comment"
            )
    return missing


def post_publication_comments(api, pr, updates):
    comments = api.pr_comments(pr["number"])
    for _, body in missing_publication_comments(comments, updates):
        api.create_pr_comment(pr["number"], body)


def expected_servlet_images(repo_root, trusted_sha, tag):
    with tempfile.TemporaryDirectory(prefix="smoke-test-adoption-") as directory:
        source = Path(directory) / "source"
        run_process(
            ["git", "worktree", "add", "--detach", str(source), trusted_sha],
            cwd=repo_root,
        )
        try:
            run_process(
                [
                    "./gradlew",
                    ":smoke-tests:images:servlet:writePublicationImages",
                    "--no-configuration-cache",
                    f"-PextraTag={tag}",
                ],
                cwd=source,
                env={**os.environ, "GITHUB_REPOSITORY": REPOSITORY},
            )
            references_file = source / "smoke-tests/images/servlet/build/publication-images.txt"
            references = set(references_file.read_text(encoding="utf-8").splitlines())
            if not references or any(not reference.endswith(f"-{tag}") for reference in references):
                raise AdoptionError("trusted servlet source generated an invalid image family")
            return references
        finally:
            run_process(["git", "worktree", "remove", "--force", str(source)], cwd=repo_root)


def expected_images_for(family_name, tag, repo_root, trusted_sha):
    if family_name == "servlet":
        return expected_servlet_images(repo_root, trusted_sha, tag)
    if family_name == "fake-backend":
        return expected_references(family_name, tag)
    reusable_workflow = run_process(
        [
            "git",
            "show",
            f"{trusted_sha}:.github/workflows/reusable-publish-smoke-test-images.yml",
        ],
        cwd=repo_root,
    )
    latest_java = re.search(
        r"^\s*LATEST_JAVA_VERSION:\s*([0-9]+)\b", reusable_workflow, re.MULTILINE
    )
    publisher_workflow = run_process(
        [
            "git",
            "show",
            f"{trusted_sha}:.github/workflows/publish-smoke-test-{family_name}-images.yml",
        ],
        cwd=repo_root,
    )
    if latest_java is None:
        raise AdoptionError("trusted publisher has no supported latest-Java version")
    versions = ("8", "11", "17", "21", "25", latest_java.group(1))
    if len(set(versions)) != len(versions):
        raise AdoptionError("trusted publisher repeats a Java image variant")
    skipped = set(
        re.findall(
            r"^\s*skip-java-([0-9]+):\s*true\b",
            publisher_workflow,
            re.MULTILINE,
        )
    )
    return expected_references(
        family_name, tag, [version for version in versions if version not in skipped]
    )


def manifest_digest(reference):
    output = run_process(
        [
            "docker",
            "buildx",
            "imagetools",
            "inspect",
            reference,
            "--format",
            "{{.Manifest.Digest}}",
        ]
    ).strip()
    if not DIGEST_PATTERN.fullmatch(output):
        raise AdoptionError(f"registry did not return a manifest digest for {reference}")
    return output


def run_is_from_family(run, family_name):
    family = FAMILIES[family_name]
    return (
        run.get("name") == family.workflow_name
        and run.get("path") == f".github/workflows/{family.workflow_file}"
        and run.get("head_branch") == BASE_BRANCH
        and run.get("event") in ("push", "workflow_dispatch")
        and run.get("status") == "completed"
        and run.get("conclusion") == "success"
    )


def latest_successful_run(api, family_name, main_sha):
    runs = [
        run
        for run in api.workflow_runs(family_name)
        if run_is_from_family(run, family_name)
    ]
    if not runs:
        return None
    winner = runs[0]
    for run in runs[1:]:
        if run["head_sha"] == winner["head_sha"]:
            if run_order(run) > run_order(winner):
                winner = run
            continue
        status = api.compare(winner["head_sha"], run["head_sha"])
        if status == "ahead":
            winner = run
        elif status == "diverged":
            raise AdoptionError("successful publication runs do not share a trusted source lineage")
    if api.compare(winner["head_sha"], main_sha) not in ("ahead", "identical"):
        raise AdoptionError("latest publication source is not an ancestor of trusted main")
    return winner


def resolve_pinned_run(api, family_name, version):
    date, suffix = version.split(".", 1)
    attempts = [(suffix, 1, True)]
    if len(suffix) > 6:
        attempts.append((suffix[:-6], int(suffix[-6:]), False))
    for run_id, attempt, legacy in attempts:
        try:
            run = api.run_attempt(run_id, attempt)
        except NotFound:
            continue
        if not run_is_from_family(run, family_name):
            continue
        date_from_run = datetime.fromisoformat(
            (run.get("run_started_at") or run["created_at"]).replace("Z", "+00:00")
        ).astimezone(timezone.utc).strftime("%Y%m%d")
        expected = f"{date_from_run}.{run_id}" if legacy else version
        valid_version = (
            date == date_from_run
            if legacy
            else publication_id_matches(run, version)
        )
        if expected == version and valid_version:
            return run
    raise AdoptionError(
        f"cannot verify provenance for currently pinned {family_name} version {version}"
    )


def verify_eligible_run(api, run, family_name, main_sha, repo_root):
    run_id = run["id"]
    attempt = run["run_attempt"]
    exact_run = api.run_attempt(run_id, attempt)
    validate_run_identity(exact_run, family_name, run_id, attempt)
    if api.compare(exact_run["head_sha"], main_sha) not in ("ahead", "identical"):
        raise AdoptionError("publisher source commit is not on trusted main")
    verify_required_jobs(api.run_jobs(run_id, attempt), family_name)
    artifact_name = f"smoke-test-publication-{family_name}-{attempt}"
    artifacts = [
        artifact
        for artifact in api.run_artifacts(run_id)
        if artifact.get("name") == artifact_name
        and not artifact.get("expired")
        and artifact.get("workflow_run", {}).get("id") == run_id
        and artifact.get("workflow_run", {}).get("head_sha") == exact_run["head_sha"]
    ]
    if len(artifacts) != 1:
        raise AdoptionError(
            "exact publisher run/attempt has no unique unexpired candidate artifact"
        )
    candidate = api.download_artifact(artifacts[0]["id"])
    tag = candidate.get("publication_id") if isinstance(candidate, dict) else None
    if not publication_id_matches(exact_run, tag):
        raise AdoptionError("candidate publication identifier does not match the exact run")
    expected = expected_images_for(family_name, tag, repo_root, main_sha)
    image_digests = validate_candidate(
        candidate, exact_run, family_name, run_id, attempt, expected
    )
    for reference, expected_digest in image_digests.items():
        if manifest_digest(reference) != expected_digest:
            raise AdoptionError(f"registry manifest changed or is unavailable for {reference}")
    return (
        exact_run,
        tag,
        f"https://github.com/{REPOSITORY}/actions/runs/{run_id}/attempts/{attempt}",
    )


def family_for_run(run):
    family_name = next(
        (
            name
            for name, family in FAMILIES.items()
            if run.get("path") == f".github/workflows/{family.workflow_file}"
            and run.get("name") == family.workflow_name
        ),
        None,
    )
    if family_name is None:
        raise AdoptionError("workflow run does not use a supported publisher workflow")
    return family_name


def validate_manual_run(api, run_id, attempt, main_sha, repo_root, current_files):
    run = api.run_attempt(run_id, attempt)
    family_name = family_for_run(run)
    validate_run_identity(run, family_name, run_id, attempt)
    exact_run, tag, run_url = verify_eligible_run(
        api, run, family_name, main_sha, repo_root
    )
    current_version = image_pin(current_files, family_name)
    if current_version == tag:
        return (
            "Dry run; no branch or PR writes performed.\n"
            f"{family_name}: {tag} is already selected by the open PR or main.",
            False,
        )
    previous_run = resolve_pinned_run(api, family_name, current_version)
    if not is_newer_run(previous_run, exact_run, api.compare):
        return (
            "Dry run; no branch or PR writes performed.\n"
            f"{family_name}: run {run_id} is verified but older than the selected version.",
            False,
        )
    return (
        "Dry run; no branch or PR writes performed.\n"
        f"{family_name}: {current_version} -> {tag} "
        f"(verified publication {run_url}).",
        True,
    )


def local_git(args, root, env=None):
    return run_process(["git", *args], cwd=root, env=env)


def read_pin_files(root, ref):
    return {
        path: local_git(["show", f"{ref}:{path}"], root)
        for path in PIN_FILES
    }


def remote_head(root, branch):
    output = local_git(["ls-remote", "--heads", "origin", branch], root)
    return output.split()[0] if output.split() else None


def commit_records(root, base_ref, branch_ref):
    output = local_git(
        [
            "log",
            "--first-parent",
            "--format=%an%x09%ae%x09%cn%x09%ce%x09%s%x09%P",
            f"{base_ref}..{branch_ref}",
        ],
        root,
    )
    return [
        tuple(line.split("\t", 5))
        for line in output.splitlines()
        if line
    ]


def validate_branch_commits(root, commits, base_ref):
    bot_commits = []
    for author, email, committer, committer_email, subject, parents in commits:
        if (
            author != BOT_NAME
            or email != BOT_EMAIL
            or committer != BOT_NAME
            or committer_email != BOT_EMAIL
        ):
            raise AdoptionError(
                "adoption branch contains an unexpected commit; preserve it and retry manually"
            )
        parent_shas = parents.split()
        if len(parent_shas) == 1 and subject == TITLE:
            bot_commits.append(
                (author, email, committer, committer_email, subject)
            )
        elif len(parent_shas) == 2 and subject == MERGE_TITLE:
            try:
                local_git(
                    ["merge-base", "--is-ancestor", parent_shas[1], base_ref],
                    root,
                )
            except AdoptionError:
                raise AdoptionError(
                    "adoption branch contains a merge from unexpected history"
                ) from None
        else:
            raise AdoptionError(
                "adoption branch contains an unexpected commit; preserve it and retry manually"
            )
    return bot_commits


def diff_paths(root, base_ref, branch_ref):
    return set(
        local_git(
            ["diff", "--name-only", f"{base_ref}...{branch_ref}"], root
        ).splitlines()
    )


def prepare_branch(root, api):
    if local_git(["status", "--porcelain"], root).strip():
        raise AdoptionError("working tree is not clean; no adoption changes were made")
    local_git(
        [
            "fetch",
            "--no-tags",
            "origin",
            f"refs/heads/{BASE_BRANCH}:refs/remotes/origin/{BASE_BRANCH}",
        ],
        root,
    )
    base_ref = f"refs/remotes/origin/{BASE_BRANCH}"
    base_files = read_pin_files(root, base_ref)
    prs = api.open_adoption_prs()
    if len(prs) > 1:
        raise AdoptionError("more than one open smoke-test adoption PR exists")
    branch_ref = f"refs/remotes/origin/{ADOPTION_BRANCH}"
    existing_head = remote_head(root, ADOPTION_BRANCH)
    pr = prs[0] if prs else None
    if pr and pr.get("base", {}).get("ref") != BASE_BRANCH:
        raise AdoptionError("the existing adoption branch PR has an unexpected base branch")
    if existing_head:
        local_git(
            ["fetch", "--no-tags", "origin", f"refs/heads/{ADOPTION_BRANCH}:{branch_ref}"],
            root,
        )
        fork_point = local_git(["merge-base", base_ref, branch_ref], root).strip()
        fork_files = read_pin_files(root, fork_point)
        paths = diff_paths(root, fork_point, branch_ref)
        if not paths or not paths.issubset(set(PIN_FILES)):
            raise AdoptionError(
                "adoption branch contains unexpected files; preserve it and retry manually"
            )
        branch_files = read_pin_files(root, branch_ref)
        commits = commit_records(root, fork_point, branch_ref)
        bot_commits = validate_branch_commits(root, commits, base_ref)
        validate_bot_branch(fork_files, branch_files, bot_commits)
        local_git(["checkout", "--detach", branch_ref], root)
        try:
            local_git(
                [
                    "-c",
                    f"user.name={BOT_NAME}",
                    "-c",
                    f"user.email={BOT_EMAIL}",
                    "merge",
                    "--no-edit",
                    "-m",
                    MERGE_TITLE,
                    base_ref,
                ],
                root,
            )
        except AdoptionError:
            try:
                local_git(["merge", "--abort"], root)
            except AdoptionError:
                pass
            raise AdoptionError(
                "main conflicts with pending adoption pins; preserve the branch and "
                "resolve manually"
            ) from None
        branch_files = read_pin_files(root, "HEAD")
        expected_head = existing_head
    else:
        if pr:
            raise AdoptionError(
                "open adoption PR has no source branch; preserve it and inspect manually"
            )
        branch_files = dict(base_files)
        local_git(["checkout", "--detach", base_ref], root)
        expected_head = None
    return base_ref, base_files, branch_files, pr, expected_head


def publication_updates(api, base_files, branch_files, verified):
    updates = []
    for family in FAMILIES:
        old_version = image_pin(base_files, family)
        new_version = image_pin(branch_files, family)
        if old_version == new_version:
            continue
        if family in verified:
            tag, run_url, run_id, attempt = verified[family]
            if tag != new_version:
                raise AdoptionError("verified publication does not match the proposed pin")
        else:
            run = resolve_pinned_run(api, family, new_version)
            run_id = str(run["id"])
            attempt = int(run["run_attempt"])
            run_url = (
                f"https://github.com/{REPOSITORY}/actions/runs/"
                f"{run_id}/attempts/{attempt}"
            )
        updates.append((family, old_version, new_version, str(run_id), attempt, run_url))
    return updates


def publish_update(root, api, branch_files, pr, expected_head, updates):
    changed_paths = [
        path for path in PIN_FILES if branch_files[path] != read_pin_files(root, "HEAD")[path]
    ]
    if changed_paths:
        for path in changed_paths:
            Path(root, path).write_text(branch_files[path], encoding="utf-8")
        local_git(["add", *changed_paths], root)
        run_process([".github/scripts/use-cla-approved-bot.sh"], cwd=root)
        local_git(["commit", "-m", TITLE], root)
    require_expected_remote_head(expected_head, remote_head(root, ADOPTION_BRANCH))
    token = os.environ.get("GH_TOKEN")
    if not token:
        raise AdoptionError("GitHub App token is required to publish an adoption PR")
    api.token = token
    git_env = {**os.environ, "GH_TOKEN": token}
    run_process(["gh", "auth", "setup-git"], cwd=root, env=git_env)
    push_args = ["push", "origin", f"HEAD:refs/heads/{ADOPTION_BRANCH}"]
    if expected_head is None:
        push_args.insert(1, "--set-upstream")
    try:
        run_process(["git", *push_args], cwd=root, env=git_env)
    except AdoptionError:
        raise AdoptionError(
            "non-force push refused a concurrent adoption-branch change; "
            "inspect the open PR and retry"
        ) from None
    if pr:
        post_publication_comments(api, pr, updates)
        return f"Updated smoke-test adoption PR #{pr['number']}."
    if not updates:
        return "No newer verified smoke-test image publications to propose."
    created = api.create_pr(PR_BODY)
    post_publication_comments(api, created, updates)
    return f"Opened draft smoke-test adoption PR #{created['number']}."


def event_run(event_path, manual_run_id=None, manual_attempt=None):
    if manual_run_id is not None:
        if not str(manual_run_id).isdecimal() or not str(manual_attempt).isdecimal():
            raise AdoptionError("manual validation requires a numeric run ID and attempt")
        return str(manual_run_id), int(manual_attempt), None
    event = json.loads(Path(event_path).read_text(encoding="utf-8"))
    run = event.get("workflow_run")
    if not isinstance(run, dict):
        raise AdoptionError("workflow_run event has no publisher run")
    repository = event.get("repository", {}).get("full_name", "")
    head_repository = run.get("head_repository") or {}
    if (
        not str(run.get("id", "")).isdecimal()
        or not str(run.get("run_attempt", "")).isdecimal()
        or run.get("conclusion") != "success"
        or run.get("head_branch") != BASE_BRANCH
        or repository.lower() != REPOSITORY
        or head_repository.get("full_name", "").lower() != REPOSITORY
    ):
        raise AdoptionError("workflow_run event is not a trusted successful main publication")
    return str(run["id"]), int(run["run_attempt"]), run


def reconcile(api, root, trigger, apply):
    main_sha = api.main_sha()
    _, base_files, current_files, pr, expected_head = prepare_branch(root, api)
    initial_files = dict(current_files)
    verified_runs = {}
    if trigger is not None:
        event_run_id, event_attempt, event = trigger
        trigger_run = api.run_attempt(event_run_id, event_attempt)
        family_name = family_for_run(trigger_run)
        validate_run_identity(trigger_run, family_name, event_run_id, event_attempt)
        if event is None:
            return validate_manual_run(
                api, event_run_id, event_attempt, main_sha, root, current_files
            )
        if event and (
            event.get("head_sha") != trigger_run.get("head_sha")
            or event.get("name") != trigger_run.get("name")
            or event.get("run_attempt") != trigger_run.get("run_attempt")
        ):
            raise AdoptionError("workflow_run event does not match the exact publisher attempt")
        verified_runs[(str(event_run_id), int(event_attempt))] = verify_eligible_run(
            api, trigger_run, family_name, main_sha, root
        )

    verified = {}
    for family_name in FAMILIES:
        selected = {
            family: image_pin(current_files, family)
            for family in FAMILIES
        }
        latest = latest_successful_run(api, family_name, main_sha)
        if latest is None:
            continue
        try:
            latest_attempt = api.run_attempt(latest["id"], latest["run_attempt"])
        except NotFound:
            raise AdoptionError(f"latest {family_name} publisher attempt is unavailable") from None
        tag = validate_run_identity(
            latest_attempt, family_name, latest["id"], latest["run_attempt"]
        )
        started = datetime.fromisoformat(
            (latest_attempt.get("run_started_at") or latest_attempt["created_at"]).replace(
                "Z", "+00:00"
            )
        ).astimezone(timezone.utc)
        legacy_tag = f"{started:%Y%m%d}.{latest_attempt['id']}"
        if selected[family_name] in (tag, legacy_tag):
            continue
        run_key = (str(latest_attempt["id"]), int(latest_attempt["run_attempt"]))
        if run_key in verified_runs:
            exact_run, tag, run_url = verified_runs[run_key]
        else:
            exact_run, tag, run_url = verify_eligible_run(
                api, latest_attempt, family_name, main_sha, root
            )
        old_run = resolve_pinned_run(api, family_name, selected[family_name])
        if not is_newer_run(old_run, exact_run, api.compare):
            continue
        updated, _ = update_pin(current_files, family_name, tag)
        current_files = updated
        verified[family_name] = (
            tag,
            run_url,
            str(exact_run["id"]),
            int(exact_run["run_attempt"]),
        )
    updates = publication_updates(api, base_files, current_files, verified)
    missing_comments = (
        missing_publication_comments(
            api.pr_comments(pr["number"]) if pr else [],
            updates,
        )
    )
    branch_needs_push = (
        pr is not None
        and expected_head != local_git(["rev-parse", "HEAD"], root).strip()
    )
    if not apply:
        changes = [
            f"{name}: {image_pin(initial_files, name)} -> {image_pin(current_files, name)}"
            for name in FAMILIES
            if image_pin(initial_files, name) != image_pin(current_files, name)
        ]
        pending = any(
            image_pin(base_files, name) != image_pin(current_files, name)
            for name in FAMILIES
        )
        return (
            "Dry run; no branch or PR writes performed.\n"
            + ("\n".join(changes) if changes else "No newer verified image versions found."),
            bool(missing_comments) or (pending and pr is None) or branch_needs_push,
        )
    if not updates and not pr:
        return "No newer verified smoke-test image publications to propose.", False
    if pr and not missing_comments and not branch_needs_push:
        return "No newer verified smoke-test image publications to propose.", False
    return (
        publish_update(root, api, current_files, pr, expected_head, updates),
        True,
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--event-path", default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--run-id")
    parser.add_argument("--run-attempt")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if args.dry_run == args.apply:
        parser.error("choose exactly one of --dry-run or --apply")
    if not args.event_path and not args.run_id:
        parser.error("provide a workflow event or a manual run ID/attempt")
    repository = os.environ.get("GITHUB_REPOSITORY", REPOSITORY)
    token = os.environ.get("GH_READ_TOKEN") or os.environ.get("GH_TOKEN")
    try:
        api = GitHub(token, repository)
        trigger = None
        if args.run_id:
            if not args.run_attempt:
                raise AdoptionError("manual validation requires --run-attempt")
            trigger = event_run(None, args.run_id, args.run_attempt)
        elif args.event_path:
            trigger = event_run(args.event_path)
        root = Path(__file__).resolve().parents[2]
        message, has_updates = reconcile(api, root, trigger, args.apply)
        print(message)
        if args.dry_run and os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"has-updates={str(has_updates).lower()}\n")
    except (AdoptionError, OSError, ValueError, KeyError) as error:
        raise SystemExit(f"Smoke-test image adoption refused: {error}") from error


if __name__ == "__main__":
    main()
