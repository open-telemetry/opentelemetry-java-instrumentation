#!/usr/bin/env python3
"""Verify a published smoke-test image family and write its completion record."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess


def create_candidate(family, repository, commit, run_id, run_attempt, workflow, tag,
                     references, results, inspect):
    if not results or any(result != "success" for result in results):
        raise ValueError("all required publication jobs must succeed")
    if not re.fullmatch(r"[a-z0-9-]+", family):
        raise ValueError("invalid family")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("invalid repository")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("invalid source commit")
    if not re.fullmatch(r"[0-9]+", run_id) or not re.fullmatch(r"[0-9]+", run_attempt):
        raise ValueError("invalid workflow run")
    if not workflow or not re.fullmatch(r"[0-9]{8}\.[0-9]+", tag):
        raise ValueError("invalid workflow or publication identifier")
    prefix = f"ghcr.io/{repository.lower()}/smoke-test-"
    if not references or len(set(references)) != len(references):
        raise ValueError("missing or duplicate required image references")

    images = []
    for reference in sorted(references):
        image, separator, image_tag = reference.rpartition(":")
        if (not image.startswith(prefix)
                or not re.fullmatch(r"[a-z0-9./_-]+", image)
                or not separator
                or not re.fullmatch(r"[\w][\w.-]{0,127}", image_tag, re.ASCII)
                or not (image_tag == tag or image_tag.endswith(f"-{tag}"))):
            raise ValueError(f"invalid image reference: {reference}")
        digest = inspect(reference)
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise ValueError(f"missing manifest digest for {reference}")
        images.append({"reference": reference, "digest": digest})
    return {
        "schema_version": 1,
        "family": family,
        "repository": repository,
        "source_commit": commit,
        "workflow": workflow,
        "run_id": run_id,
        "run_attempt": run_attempt,
        "publication_id": tag,
        "images": images,
    }


def inspect_manifest(reference):
    result = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", reference,
         "--format", "{{.Manifest.Digest}}"],
        check=True, capture_output=True, text=True,
    )
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--references", required=True, type=Path)
    parser.add_argument("--results", required=True, nargs="+")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    args.output.unlink(missing_ok=True)
    try:
        candidate = create_candidate(
            args.family, os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"],
            os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"],
            os.environ["GITHUB_WORKFLOW_REF"], args.tag,
            args.references.read_text().splitlines(), args.results, inspect_manifest,
        )
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Publication verification failed: {error}") from error
    args.output.write_text(json.dumps(candidate, indent=2) + "\n")


if __name__ == "__main__":
    main()
