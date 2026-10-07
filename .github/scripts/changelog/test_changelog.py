import tomllib
import unittest
from pathlib import Path

from jinja2 import Template


class ChangelogTemplateTest(unittest.TestCase):
    def test_formatting(self):
        root = Path(__file__).resolve().parents[3]
        config = tomllib.loads((root / "towncrier.toml").read_text(encoding="utf-8"))[
            "tool"
        ]["towncrier"]
        template = Template(
            (root / config["template"]).read_text(encoding="utf-8"), trim_blocks=True
        )
        result = template.render(
            definitions={item["directory"]: item for item in config["type"]},
            sections={
                "": {
                    "bugfix": {
                        "Fix missing spans.": [
                            config["issue_format"].format(issue=2),
                            config["issue_format"].format(issue=3),
                        ],
                        "Clarify migration guidance.": [],
                    },
                    "breaking": {
                        "Remove `old`.\n  Use `replacement`.": [
                            config["issue_format"].format(issue=1),
                        ],
                    },
                },
            },
        )
        self.assertEqual(
            result,
            "### ⚠️ Breaking changes\n\n"
            "- Remove `old`.\n"
            "  Use `replacement`.\n"
            "  ([#1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/1))\n"
            "\n"
            "### 🛠️ Bug fixes\n\n"
            "- Fix missing spans.\n"
            "  ([#2](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2), "
            "[#3](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/3))\n"
            "- Clarify migration guidance.\n"
            "\n",
        )


if __name__ == "__main__":
    unittest.main()
