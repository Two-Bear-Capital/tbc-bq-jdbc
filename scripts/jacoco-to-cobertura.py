#!/usr/bin/env python3
"""Convert a JaCoCo XML report to Cobertura XML.

GitHub's code coverage API (actions/upload-code-coverage) accepts Cobertura XML
only, and JaCoCo has no Cobertura writer. Rather than pull in a third-party
converter action or download a script at CI time, the transform lives here where
it can be read and reviewed.

Coverage is emitted one Cobertura <class> per JaCoCo <sourcefile>, not per Java
class. JaCoCo records line coverage against the source file, so splitting it back
out per class means guessing which lines belong to nested, inner, anonymous and
synthetic classes -- and getting that wrong silently misattributes lines. The
consumer here reports coverage per file and per line, so the sourcefile grouping
is both simpler and closer to the source of truth.

Usage:
    jacoco-to-cobertura.py <jacoco.xml> <cobertura.xml> [source-root ...]
"""

import sys
import time
import xml.etree.ElementTree as ET


def rate(covered, missed):
    """Cobertura expresses coverage as a 0..1 rate. No counters means 100%."""
    total = covered + missed
    return covered / total if total else 1.0


def counters(node):
    """Sum JaCoCo <counter> children into {type: (covered, missed)}."""
    out = {}
    for c in node.findall("counter"):
        out[c.get("type")] = (int(c.get("covered", 0)), int(c.get("missed", 0)))
    return out


def counter(node, kind):
    return counters(node).get(kind, (0, 0))


def convert(jacoco_path, cobertura_path, sources):
    report = ET.parse(jacoco_path).getroot()

    lines_covered, lines_missed = counter(report, "LINE")
    branches_covered, branches_missed = counter(report, "BRANCH")

    coverage = ET.Element(
        "coverage",
        {
            "line-rate": f"{rate(lines_covered, lines_missed):.4f}",
            "branch-rate": f"{rate(branches_covered, branches_missed):.4f}",
            "lines-covered": str(lines_covered),
            "lines-valid": str(lines_covered + lines_missed),
            "branches-covered": str(branches_covered),
            "branches-valid": str(branches_covered + branches_missed),
            "complexity": "0",
            "version": "jacoco-to-cobertura",
            "timestamp": str(int(time.time() * 1000)),
        },
    )

    source_el = ET.SubElement(coverage, "sources")
    for src in sources:
        ET.SubElement(source_el, "source").text = src

    packages_el = ET.SubElement(coverage, "packages")

    for package in report.findall("package"):
        # JaCoCo uses slash-separated internal names; Cobertura uses dots.
        pkg_path = package.get("name", "")
        pkg_name = pkg_path.replace("/", ".")

        pkg_lines_c, pkg_lines_m = counter(package, "LINE")
        pkg_branch_c, pkg_branch_m = counter(package, "BRANCH")

        package_el = ET.SubElement(
            packages_el,
            "package",
            {
                "name": pkg_name,
                "line-rate": f"{rate(pkg_lines_c, pkg_lines_m):.4f}",
                "branch-rate": f"{rate(pkg_branch_c, pkg_branch_m):.4f}",
                "complexity": "0",
            },
        )
        classes_el = ET.SubElement(package_el, "classes")

        for sourcefile in package.findall("sourcefile"):
            filename = sourcefile.get("name")
            src_lines_c, src_lines_m = counter(sourcefile, "LINE")
            src_branch_c, src_branch_m = counter(sourcefile, "BRANCH")

            stem = filename[:-5] if filename.endswith(".java") else filename
            class_el = ET.SubElement(
                classes_el,
                "class",
                {
                    "name": f"{pkg_name}.{stem}" if pkg_name else stem,
                    "filename": f"{pkg_path}/{filename}" if pkg_path else filename,
                    "line-rate": f"{rate(src_lines_c, src_lines_m):.4f}",
                    "branch-rate": f"{rate(src_branch_c, src_branch_m):.4f}",
                    "complexity": "0",
                },
            )
            # Cobertura readers expect <methods> before <lines>, even when empty.
            # Per-method data is dropped deliberately: see the module docstring.
            ET.SubElement(class_el, "methods")
            lines_el = ET.SubElement(class_el, "lines")

            for line in sourcefile.findall("line"):
                covered_instructions = int(line.get("ci", 0))
                missed_branches = int(line.get("mb", 0))
                covered_branches = int(line.get("cb", 0))
                total_branches = missed_branches + covered_branches

                attrs = {
                    "number": line.get("nr"),
                    # JaCoCo records whether a line ran, not how many times.
                    "hits": "1" if covered_instructions > 0 else "0",
                    "branch": "true" if total_branches else "false",
                }
                if total_branches:
                    pct = int(100 * covered_branches / total_branches)
                    attrs["condition-coverage"] = (
                        f"{pct}% ({covered_branches}/{total_branches})"
                    )
                ET.SubElement(lines_el, "line", attrs)

    tree = ET.ElementTree(coverage)
    ET.indent(tree, space="  ")
    with open(cobertura_path, "wb") as fh:
        fh.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
        fh.write(
            b'<!DOCTYPE coverage SYSTEM '
            b'"http://cobertura.sourceforge.net/xml/coverage-04.dtd">\n'
        )
        tree.write(fh, encoding="UTF-8", xml_declaration=False)

    return lines_covered, lines_missed, branches_covered, branches_missed


def main():
    if len(sys.argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2

    jacoco_path, cobertura_path = sys.argv[1], sys.argv[2]
    sources = sys.argv[3:] or ["src/main/java"]

    lc, lm, bc, bm = convert(jacoco_path, cobertura_path, sources)
    print(
        f"{cobertura_path}: lines {lc}/{lc + lm} ({rate(lc, lm):.1%}), "
        f"branches {bc}/{bc + bm} ({rate(bc, bm):.1%})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
