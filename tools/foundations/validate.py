#!/usr/bin/env python3
"""Validates foundation files against the pools (see docs/FOUNDATIONS.md).

Usage:
  python3 tools/foundations/validate.py                 # app/src/main/assets/foundations/*.json
  python3 tools/foundations/validate.py FILE POOL_ID    # one draft file against one pool

Exits non-zero and lists every problem. The app's FoundationAssetsTest is
the final gate; this script gives authors the same feedback without Gradle.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
POOLS = ("technician", "general", "extra")
sys.path.insert(0, str(ROOT / "tools/aids"))
import validate as aids  # noqa: E402

ID = re.compile(r"^[tge]-[a-z0-9]+(-[a-z0-9]+)*$")
NUMBER = re.compile(r"\d[\d,]*(?:\.\d+)?|\.\d+")
AID_KINDS = {"bandplan", "spectrum", "repeater", "modes", "ionosphere", "codes", "antenna", "filter", "logic", "component"}
LIMITS = {"title": 40, "summary": 140, "hook": 160}


BAND_NAME = re.compile(r"(\d+(?:\.\d+)?)[ -]?(meters?|m|cm|centimeters?)\b")


def band_numbers(text):
    """Numbers in text that name a real amateur band ("30 meters", "70 cm")."""
    out = []
    for n, unit in BAND_NAME.findall(text or ""):
        band = f"{n}{'cm' if unit.startswith('c') else 'm'}"
        if band in aids.BANDS:
            out.append(float(n))
    return out


def numbers(text):
    """Every number in text as a float, commas ignored."""
    return [float(n.replace(",", "")) for n in NUMBER.findall(text or "")]


def contains(text, n):
    return any(abs(x - n) < 1e-9 for x in numbers(text))


def full_text(q):
    return " ".join([q["question"], *q["answers"]])


def check(f, questions, owner, problems):
    fid = f.get("id")
    say = lambda msg: problems.append(f"{fid}: {msg}")
    if not isinstance(fid, str) or not ID.match(fid):
        say("id must be kebab case starting with t-, g- or e-")
    for key, limit in LIMITS.items():
        v = f.get(key)
        if v is None and key == "hook":
            continue
        if not isinstance(v, str) or not v.strip():
            say(f"{key} must be a non-empty string")
        elif len(v) > limit:
            say(f"{key} is {len(v)} chars (max {limit})")
    for v in re.findall(r'"([^"]*)"', json.dumps(f, ensure_ascii=False)):
        if re.search("[–—]", v):
            say("em/en dash found")
            break

    qs = f.get("questions")
    if not isinstance(qs, list) or len(qs) < 3:
        return say("questions needs 3 or more ids")
    for q in qs:
        if q not in questions:
            say(f"{q} is not in this pool")
        elif q in owner:
            say(f"{q} already belongs to {owner[q]}")
        else:
            owner[q] = fid
    if len(set(qs)) != len(qs):
        say("questions has duplicates")

    points = f.get("points")
    if not isinstance(points, list) or not 2 <= len(points) <= 8:
        return say("points needs 2 to 8 rows")
    point_text = []
    for i, p in enumerate(points):
        label, value, src = p.get("label"), p.get("value"), p.get("q")
        where = f"point {i + 1}"
        if not isinstance(label, str) or not label.strip() or len(label) > 28:
            say(f"{where}: label must be 1 to 28 chars")
            continue
        if not isinstance(value, str) or not value.strip() or len(value) > 48:
            say(f"{where}: value must be 1 to 48 chars")
            continue
        if not isinstance(src, list) or not src:
            say(f"{where}: q must list the question(s) it answers")
            continue
        if any(s not in qs for s in src):
            say(f"{where}: q {src} must all be in questions")
            continue
        known = [questions[s] for s in src if s in questions]
        for n in numbers(value):
            if not any(contains(q["answers"][q["correct"]], n) for q in known):
                say(f"{where}: {n:g} in value is not in a correct answer of {src}")
        for n in numbers(label):
            if n not in band_numbers(label) and not any(contains(full_text(q), n) for q in known):
                say(f"{where}: {n:g} in label is not in the text of {src}")
        point_text += [label, value]

    pool_text = " ".join(full_text(questions[q]) for q in qs if q in questions)
    for key in ("summary", "hook"):
        for n in numbers(f.get(key) or ""):
            small = n == int(n) and 1 <= n <= 12
            named_band = n in band_numbers(f.get(key))
            if not small and not named_band and not contains(" ".join(point_text), n) and not contains(pool_text, n):
                say(f"{key}: {n:g} is not in a point or a linked question")

    aid = f.get("aid")
    if aid is not None:
        if not isinstance(aid, dict) or aid.get("kind") not in AID_KINDS:
            say(f"aid kind must be one of {sorted(AID_KINDS)}")
        else:
            blank = {"question": "", "answers": [""], "correct": 0}
            aid_problems = []
            aids.check_entry({**aid, "q": fid}, blank, aid_problems)
            problems.extend(aid_problems)


def validate(entries, pool_id):
    pool = json.loads((ASSETS / f"pools/{pool_id}.json").read_text())
    questions = {q["id"]: q for q in pool["questions"]}
    if not isinstance(entries, list):
        return ["file must be a JSON array"]
    problems, owner, ids = [], {}, set()
    for f in entries:
        if f.get("id") in ids:
            problems.append(f"{f.get('id')}: duplicate id")
        ids.add(f.get("id"))
        check(f, questions, owner, problems)
    return problems


def main():
    if len(sys.argv) == 3:
        targets = [(Path(sys.argv[1]), sys.argv[2])]
    else:
        targets = [(ASSETS / f"foundations/{p}.json", p) for p in POOLS]
    failed = False
    for path, pool_id in targets:
        entries = json.loads(path.read_text())
        problems = validate(entries, pool_id)
        covered = sum(len(f.get("questions", [])) for f in entries)
        print(f"{path.name}: {len(entries)} foundations, {covered} questions, {len(problems)} problems")
        for p in problems:
            print("  " + p)
        failed |= bool(problems)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
