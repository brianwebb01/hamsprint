#!/usr/bin/env python3
"""Build HamSprint's question-pool assets from the official NCVEC Word documents.

The NCVEC Question Pool Committee publishes each pool (public domain) as a .docx
with errata folded in. This script parses those documents into the JSON the app
ships, and extracts the embedded figures. Its outputs are committed, so building
the app never requires Python; re-run this only when NCVEC issues new errata.

    python3 tools/pool/build_pools.py

Requires Pillow (figure processing). Fails loudly on any structural surprise:
a silently malformed pool is worse than a failed build.
"""
from __future__ import annotations

import io
import json
import re
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "pool" / "source"
ASSETS = ROOT / "app" / "src" / "main" / "assets"


@dataclass(frozen=True)
class PoolSpec:
    id: str
    element: int
    name: str
    docx: str
    pool_name: str
    valid_from: str
    valid_through: str
    revision: str
    exam_questions: int
    passing_score: int
    # Figure names in the order their images are embedded in the document.
    figures: tuple[str, ...]


SPECS = (
    PoolSpec(
        id="technician", element=2, name="Technician",
        docx="technician-2026-2030-feb-19-2026.docx",
        pool_name="2026-2030 Technician Class (Element 2)",
        valid_from="2026-07-01", valid_through="2030-06-30",
        revision="Errata of February 19, 2026",
        exam_questions=35, passing_score=26,
        figures=("T-1", "T-2", "T-3"),
    ),
    PoolSpec(
        id="general", element=3, name="General",
        docx="general-2023-2027-errata6-feb-4-2026.docx",
        pool_name="2023-2027 General Class (Element 3)",
        valid_from="2023-07-01", valid_through="2027-06-30",
        revision="6th Errata of February 4, 2026",
        exam_questions=35, passing_score=26,
        figures=("G7-1",),
    ),
    PoolSpec(
        id="extra", element=4, name="Amateur Extra",
        docx="extra-2024-2028-errata4-feb-4-2026.docx",
        pool_name="2024-2028 Amateur Extra Class (Element 4)",
        valid_from="2024-07-01", valid_through="2028-06-30",
        revision="4th Errata of February 4, 2026",
        exam_questions=50, passing_score=37,
        figures=("E5-1", "E6-1", "E6-2", "E6-3", "E7-1", "E7-2", "E7-3", "E9-1", "E9-2", "E9-3"),
    ),
)

QUESTION_HEAD = re.compile(r"^([TGE]\d[A-Z]\d{2})\s*\(([A-D])\)\s*(.*)$")
DELETED_HEAD = re.compile(r"^([TGE]\d[A-Z]\d{2})\s+Question Deleted", re.I)
ANSWER = re.compile(r"^([A-D])[.)]\s*(.*)$")
SUBELEMENT = re.compile(
    r"^SUBELEMENT\s+([TGE]\d)\s*[-\u2013\u2014]\s*(.+?)\s*\[(\d+)\s+Exam Questions?\s*[-\u2013\u2014]\s*(\d+)\s+Groups?\]",
    re.I,
)
GROUP = re.compile(r"^([TGE]\d[A-Z])\s*[-\u2013\u2014]?\s+(\S.*)$")
FIGURE_REF = re.compile(r"\bfigure\s+([TGE]\d?-\d)\b", re.I)

# Words that stay upper-case when subelement titles are converted from ALL CAPS.
ACRONYMS = {"RF", "AC", "DC", "FCC", "HF", "VHF", "UHF", "EMI", "RFI", "SWR"}
MINOR = {"and", "or", "of", "the", "a", "an", "in", "on", "for", "to", "with"}


def title_case(text: str) -> str:
    words = text.replace("’", "'").lower().split()
    out = []
    for i, w in enumerate(words):
        bare = w.strip(":,;()")
        if bare.upper() in ACRONYMS:
            out.append(w.upper())
        elif i > 0 and bare in MINOR:
            out.append(w)
        else:
            out.append(w[:1].upper() + w[1:])
    return " ".join(out).replace("'", "’")


def clean(text: str) -> str:
    text = text.replace(" ", " ").replace("\t", " ")
    return re.sub(r"\s+", " ", text).strip()


def paragraphs(docx: zipfile.ZipFile) -> list[str]:
    xml = docx.read("word/document.xml").decode("utf-8")
    out = []
    token = re.compile(r"<w:t(?:\s[^>]*)?>([^<]*)</w:t>|<w:(noBreakHyphen|tab)\s*/>")
    for p in re.findall(r"<w:p[ >].*?</w:p>", xml, flags=re.S):
        # Word stores some hyphens (e.g. "E7-3") as elements rather than text.
        parts = [m.group(1) if m.group(1) is not None else ("-" if m.group(2) == "noBreakHyphen" else " ")
                 for m in token.finditer(p)]
        out.append(clean(_unescape("".join(parts))))
    return out


def _unescape(s: str) -> str:
    return (s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')
             .replace("&apos;", "'").replace("&amp;", "&"))


@dataclass
class Parsed:
    subelements: list[dict] = field(default_factory=list)
    groups: dict[str, str] = field(default_factory=dict)
    questions: list[dict] = field(default_factory=list)
    deleted: list[str] = field(default_factory=list)


def parse(lines: list[str], spec: PoolSpec) -> Parsed:
    result = Parsed()
    seen_sub: set[str] = set()
    # Errata notes come first (and may quote question headers), then the
    # syllabus, where titles are taken from, then the pool body.
    syllabus_start = next(i for i, l in enumerate(lines) if "question pool syllabus" in l.lower())
    first_q = next(i for i in range(syllabus_start, len(lines)) if QUESTION_HEAD.match(lines[i]))

    for line in lines[syllabus_start:first_q]:
        if m := SUBELEMENT.match(line):
            sid = m.group(1)
            if sid in seen_sub:
                continue
            seen_sub.add(sid)
            result.subelements.append({
                "id": sid,
                "title": title_case(m.group(2)),
                "examQuestions": int(m.group(3)),
                "groups": [],
            })
        elif (m := GROUP.match(line)) and m.group(1) not in result.groups:
            result.groups[m.group(1)] = m.group(2).rstrip(" .")

    i = first_q
    while i < len(lines):
        line = lines[i]
        if m := DELETED_HEAD.match(line):
            result.deleted.append(m.group(1))
            i += 1
            continue
        m = QUESTION_HEAD.match(line)
        if not m:
            i += 1
            continue
        qid, letter, refs = m.group(1), m.group(2), m.group(3).strip()
        i += 1
        stem: list[str] = []
        while i < len(lines) and not ANSWER.match(lines[i]):
            if lines[i]:
                stem.append(lines[i])
            i += 1
        answers: dict[str, str] = {}
        while i < len(lines) and (am := ANSWER.match(lines[i])):
            answers[am.group(1)] = am.group(2).strip()
            i += 1
        if list(answers) != ["A", "B", "C", "D"]:
            sys.exit(f"{qid}: expected answers A-D, got {list(answers)}")
        question = " ".join(stem)
        if not question:
            sys.exit(f"{qid}: empty question text")
        fig = FIGURE_REF.search(question)
        figure = fig.group(1).upper() if fig else None
        if figure and figure not in spec.figures:
            sys.exit(f"{qid}: references unknown figure {figure}")
        result.questions.append({
            "id": qid,
            "group": qid[:3],
            "question": question,
            "answers": [answers[k] for k in "ABCD"],
            "correct": "ABCD".index(letter),
            "refs": refs.strip("[] ") or None,
            "figure": figure,
        })
    return result


def assemble(spec: PoolSpec, parsed: Parsed) -> dict:
    by_group: dict[str, int] = {}
    for q in parsed.questions:
        by_group[q["group"]] = by_group.get(q["group"], 0) + 1
    ids = [q["id"] for q in parsed.questions]
    if len(ids) != len(set(ids)):
        sys.exit(f"{spec.id}: duplicate question ids")

    subs = {s["id"]: s for s in parsed.subelements}
    for gid in sorted(by_group):
        sub = subs.get(gid[:2])
        if sub is None:
            sys.exit(f"{spec.id}: group {gid} has no subelement in syllabus")
        title = parsed.groups.get(gid)
        if not title:
            sys.exit(f"{spec.id}: group {gid} has no title in syllabus")
        sub["groups"].append({"id": gid, "title": title})

    for s in parsed.subelements:
        if len(s["groups"]) != s["examQuestions"]:
            sys.exit(f"{spec.id}: {s['id']} has {len(s['groups'])} groups "
                     f"but {s['examQuestions']} exam questions")
    total_groups = sum(len(s["groups"]) for s in parsed.subelements)
    if total_groups != spec.exam_questions:
        sys.exit(f"{spec.id}: {total_groups} groups, expected {spec.exam_questions}")

    return {
        "id": spec.id,
        "element": spec.element,
        "name": spec.name,
        "poolName": spec.pool_name,
        "validFrom": spec.valid_from,
        "validThrough": spec.valid_through,
        "revision": spec.revision,
        "examQuestions": spec.exam_questions,
        "passingScore": spec.passing_score,
        "subelements": parsed.subelements,
        "questions": parsed.questions,
    }


def export_figures(docx: zipfile.ZipFile, spec: PoolSpec) -> None:
    media = sorted(
        (n for n in docx.namelist() if n.startswith("word/media/")),
        key=lambda n: int(re.search(r"(\d+)", Path(n).stem).group(1)),
    )
    if len(media) != len(spec.figures):
        sys.exit(f"{spec.id}: {len(media)} embedded images, expected {len(spec.figures)}")
    for name, figure in zip(media, spec.figures):
        img = Image.open(io.BytesIO(docx.read(name)))
        # Some NCVEC JPEGs are CMYK, which Android's decoder renders wrongly.
        img = ImageOps.grayscale(img.convert("RGB"))
        bbox = ImageOps.invert(img).getbbox()
        if bbox:
            pad = 24
            img = img.crop((max(0, bbox[0] - pad), max(0, bbox[1] - pad),
                            min(img.width, bbox[2] + pad), min(img.height, bbox[3] + pad)))
        if img.width > 1600:
            img = img.resize((1600, round(img.height * 1600 / img.width)), Image.LANCZOS)
        img.save(ASSETS / "figures" / f"{figure}.png", optimize=True)


def main() -> None:
    (ASSETS / "pools").mkdir(parents=True, exist_ok=True)
    (ASSETS / "figures").mkdir(parents=True, exist_ok=True)
    for spec in SPECS:
        with zipfile.ZipFile(SOURCE / spec.docx) as docx:
            parsed = parse(paragraphs(docx), spec)
            pool = assemble(spec, parsed)
            export_figures(docx, spec)
        out = ASSETS / "pools" / f"{spec.id}.json"
        out.write_text(json.dumps(pool, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"{spec.id}: {len(pool['questions'])} questions, "
              f"{sum(len(s['groups']) for s in pool['subelements'])} groups, "
              f"withdrawn {parsed.deleted}")


if __name__ == "__main__":
    main()
