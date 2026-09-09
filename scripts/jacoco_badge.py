#!/usr/bin/env python3
"""Render deterministic backend coverage summaries and a self-hosted SVG badge from JaCoCo XML."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE")


def percentage(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 if total == 0 else covered * 100.0 / total


def coverage_color(value: float) -> str:
    if value >= 90.0:
        return "#4c1"
    if value >= 80.0:
        return "#97ca00"
    if value >= 70.0:
        return "#a4a61d"
    if value >= 60.0:
        return "#dfb317"
    if value >= 50.0:
        return "#fe7d37"
    return "#e05d44"


def parse_counters(xml_path: Path) -> dict[str, dict[str, float | int]]:
    root = ET.parse(xml_path).getroot()
    counters: dict[str, dict[str, float | int]] = {}
    for counter_type in COUNTER_TYPES:
        counter = next(
            (node for node in root.findall("counter") if node.attrib.get("type") == counter_type),
            None,
        )
        if counter is None:
            raise SystemExit(f"JaCoCo report is missing root {counter_type} coverage")
        covered = int(counter.attrib["covered"])
        missed = int(counter.attrib["missed"])
        counters[counter_type.lower()] = {
            "covered": covered,
            "missed": missed,
            "percent": round(percentage(covered, missed), 2),
        }
    return counters


def render_svg(line_percent: float) -> str:
    label = "backend coverage"
    value = f"{line_percent:.1f}% lines"
    label_width = 118
    value_width = 78
    width = label_width + value_width
    color = coverage_color(line_percent)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="20" role="img" aria-label="{label}: {value}">
  <title>{label}: {value}</title>
  <linearGradient id="s" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <clipPath id="r"><rect width="{width}" height="20" rx="3"/></clipPath>
  <g clip-path="url(#r)">
    <rect width="{label_width}" height="20" fill="#555"/>
    <rect x="{label_width}" width="{value_width}" height="20" fill="{color}"/>
    <rect width="{width}" height="20" fill="url(#s)"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
    <text x="{label_width / 2}" y="15" fill="#010101" fill-opacity=".3">{label}</text>
    <text x="{label_width / 2}" y="14">{label}</text>
    <text x="{label_width + value_width / 2}" y="15" fill="#010101" fill-opacity=".3">{value}</text>
    <text x="{label_width + value_width / 2}" y="14">{value}</text>
  </g>
</svg>
'''


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", type=Path, required=True)
    parser.add_argument("--json", dest="json_path", type=Path, required=True)
    parser.add_argument("--svg", type=Path, required=True)
    args = parser.parse_args()

    counters = parse_counters(args.xml)
    summary = {
        "schema": 1,
        "source": str(args.xml),
        "metric": "line",
        "coverage": counters,
    }
    args.json_path.parent.mkdir(parents=True, exist_ok=True)
    args.svg.parent.mkdir(parents=True, exist_ok=True)
    args.json_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.svg.write_text(render_svg(float(counters["line"]["percent"])), encoding="utf-8")

    print(
        "Backend JaCoCo coverage: "
        f"lines={counters['line']['percent']:.2f}% "
        f"branches={counters['branch']['percent']:.2f}% "
        f"instructions={counters['instruction']['percent']:.2f}%"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
