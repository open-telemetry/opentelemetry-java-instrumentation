#!/usr/bin/env python3
"""Select maintained floating-base smoke-test image families to refresh."""

import json
import sys

from smoke_test_image_adoption import FAMILIES


REFRESH_FAMILIES = (
    "fake-backend",
    "grpc",
    "play",
    "quarkus",
    "security-manager",
    "spring-boot",
)


def selected_families(event_name, family):
    if event_name == "schedule":
        if family not in (None, "all"):
            raise ValueError("scheduled refresh does not accept a family")
        return list(REFRESH_FAMILIES)
    if event_name != "workflow_dispatch":
        raise ValueError(f"unsupported refresh event: {event_name}")
    if family == "all":
        return list(REFRESH_FAMILIES)
    if family not in REFRESH_FAMILIES:
        raise ValueError(f"unsupported refresh family: {family}")
    return [family]


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: smoke_test_image_refresh.py EVENT FAMILY")
    try:
        families = selected_families(sys.argv[1], sys.argv[2])
    except ValueError as error:
        raise SystemExit(str(error)) from error
    print(
        json.dumps(
            [
                {"family": family, "workflow": FAMILIES[family].workflow_file}
                for family in families
            ],
            separators=(",", ":"),
        )
    )


if __name__ == "__main__":
    main()
