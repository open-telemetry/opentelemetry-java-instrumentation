"""Prepare a clean PR checkout and run each Copilot pass through AWF."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from common import run, write_json


def checkout_pr(repo: str, pr: int, head: str, checkout: Path) -> None:
    if not re.fullmatch(r"[a-f0-9]{40}", head):
        raise ValueError("Invalid PR head SHA")
    checkout.mkdir()
    env = {
        **os.environ,
        "GIT_CONFIG_GLOBAL": os.devnull,
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_LFS_SKIP_SMUDGE": "1",
    }

    def git(*args: str) -> str:
        return run(
            ["git", "-c", f"core.hooksPath={os.devnull}", *args], cwd=checkout, env=env, timeout=120
        ).stdout.strip()

    git("init", "--quiet", "--template=")
    git("fetch", "--quiet", "--depth=1", "--no-tags", f"https://github.com/{repo}.git", f"refs/pull/{pr}/head")
    if git("rev-parse", "FETCH_HEAD") != head:
        raise RuntimeError("The PR head changed before checkout; start a new run")
    git("checkout", "--quiet", "--detach", head)


def run_in_sandbox(checkout: Path, inputs: Path, args: list[str]) -> subprocess.CompletedProcess[str]:
    copilot = shutil.which("copilot")
    if copilot is None:
        raise RuntimeError("Copilot CLI is not installed")
    java_home = os.environ["JAVA_HOME"]
    cli_dir = Path(copilot).parent.parent
    with tempfile.TemporaryDirectory(prefix="pass-", dir=checkout.parent) as temp:
        root = Path(temp)
        workspace = root / "checkout"
        home = root / "home"
        home.mkdir()
        shutil.copytree(checkout, workspace, symlinks=True)
        config = root / "awf.json"
        write_json(config, {
            "network": {"isolation": True, "allowDomains": [
                "services.gradle.org", "downloads.gradle.org", "plugins.gradle.org",
                "plugins-artifacts.gradle.org", "repo.maven.apache.org", "repo1.maven.org",
                "api.foojay.io", "api.adoptium.net", "github.com", "release-assets.githubusercontent.com",
                "central.sonatype.com", "dl.google.com",
            ]},
            "apiProxy": {"enabled": True, "modelFallback": {"enabled": False}},
        })
        env = {
            key: os.environ[key] for key in (
                "PATH", "USER", "LOGNAME", "LANG",
                "GITHUB_ACTIONS", "RUNNER_ENVIRONMENT", "COPILOT_GITHUB_TOKEN",
            ) if key in os.environ
        }
        env["HOME"] = str(home)
        env["GITHUB_WORKSPACE"] = str(workspace)
        return run(
            [
                "awf", "--config", str(config), "--work-dir", str(root / "awf"),
                "--image-tag", os.environ["AWF_IMAGE_TAG"], "--container-workdir", str(workspace),
                "--mount", f"{cli_dir}:{cli_dir}:ro", "--mount", f"{inputs}:{inputs}:ro",
                "--mount", f"{java_home}:{java_home}:ro", "--env", f"JAVA_HOME={java_home}",
                "--env", "GITHUB_COPILOT_PROMPT_MODE_EXTENSIONS=false",
                "--log-level", "warn",
                "--", "/bin/bash", str(inputs / "launch-copilot.sh"),
                str(workspace), str(inputs), str(config), (checkout / ".git" / "HEAD").read_text().strip(),
                copilot, *args,
            ],
            cwd=workspace, env=env, check=False, timeout=540,
        )
