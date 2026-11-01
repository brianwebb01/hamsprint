#!/usr/bin/env python3
"""Validates visual aid files against the pools (see docs/VISUAL_AIDS.md).

Usage:
  python3 tools/aids/validate.py                 # app/src/main/assets/aids/*.json
  python3 tools/aids/validate.py FILE POOL_ID    # one draft file against one pool

Exits non-zero and lists every problem. The app's VisualAidAssetsTest is the
final gate; this script gives authors the same feedback without a Gradle build.
"""
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
POOLS = ("technician", "general", "extra")

BANDS = {  # MHz edges, used only to sanity check marks
    "2200m": (0.1357, 0.1378), "630m": (0.472, 0.479), "160m": (1.8, 2.0), "80m": (3.5, 4.0),
    "60m": (5.3305, 5.4065), "40m": (7.0, 7.3), "30m": (10.1, 10.15), "20m": (14.0, 14.35),
    "17m": (18.068, 18.168), "15m": (21.0, 21.45), "12m": (24.89, 24.99), "10m": (28.0, 29.7),
    "6m": (50.0, 54.0), "2m": (144.0, 148.0), "1.25m": (219.0, 225.0), "70cm": (420.0, 450.0),
    "33cm": (902.0, 928.0), "23cm": (1240.0, 1300.0), "13cm": (2300.0, 2450.0),
}
PREFIX = {"p": -12, "n": -9, "u": -6, "m": -3, "": 0, "k": 3, "M": 6, "G": 9}
CODES = {
    "q": "QRM QRN QSY QSL QRP QRO QTH QRZ QRL QRS QRQ QRT QRV QSO QSB QRX".split(),
    "prosign": "CQ DE K KN SK AR BK 73".split(),
    "rst": ["R", "S", "T"],
}
SYMBOLS = set(
    "resistor potentiometer capacitor variable_capacitor inductor transformer diode zener led npn pnp "
    "jfet mosfet battery fuse switch relay lamp antenna ground crystal speaker".split()
)
MODES = {"FT8", "PSK31", "CW", "RTTY", "SSB", "AM", "FM", "ATV"}

WORD_PREFIX = {"pico": 1e-12, "nano": 1e-9, "micro": 1e-6, "milli": 1e-3, "kilo": 1e3, "mega": 1e6, "giga": 1e9}
SYM_PREFIX = {"p": 1e-12, "n": 1e-9, "u": 1e-6, "µ": 1e-6, "m": 1e-3, "k": 1e3, "M": 1e6, "G": 1e9}
FREQ = re.compile(r"(\d[\d,]*\.?\d*)\s*(kHz|MHz)")


def question_unit(q):
    """kHz when the question writes a radio frequency of 1 MHz or more in kHz
    (21300 kHz), MHz when it writes one in MHz, else None. The correct answer
    decides; the stem is used only when that answer names no frequency."""
    for text in (q["answers"][q["correct"]], q["question"]):
        found = [(float(n.replace(",", "")), u) for n, u in FREQ.findall(text)]
        if any(u == "kHz" and n >= 1000 for n, u in found):
            return "kHz"
        if any(u == "MHz" for n, u in found):
            return "MHz"
    return None


def check_units(e, q, say):
    """Frequencies follow the question's unit, else the pool convention: MHz
    from 1 MHz up, kHz below. Bandwidths and offsets in kHz are not checked."""
    expected = question_unit(q)
    k = e.get("kind")
    if k in ("bandplan", "spectrum") and e.get("unit", "MHz") not in ("kHz", "MHz"):
        say("unit must be kHz or MHz")
    numeric_mark = isinstance(e.get("mark"), (int, float)) and not isinstance(e.get("mark"), bool)
    if (k == "bandplan" and e.get("band") in BANDS) or (k == "spectrum" and numeric_mark):
        low = BANDS[e["band"]][0] if k == "bandplan" else e["mark"]
        conventional = "kHz" if low < 1 else "MHz"
        shown = e.get("unit", conventional)
        if shown != (expected or conventional):
            say(f"shows {shown} but the question writes frequencies in {expected or conventional}")
        if e.get("unit") == conventional:
            say("unit repeats the convention; leave it out")
    cap = e.get("caption") or ""
    if expected == "kHz" and "MHz" in cap:
        say("caption uses MHz but the question writes frequencies in kHz")
    if expected != "kHz" and any(float(n.replace(",", "")) >= 1000 for n, u in FREQ.findall(cap) if u == "kHz"):
        say("caption writes a frequency in kHz but the question uses MHz")


NUM = re.compile(r"(\d[\d,]*\.?\d*|\.\d+)\s*([a-zA-Zµ]*)")


def answer_values(text):
    """Every number in an answer, both as printed and scaled by its SI prefix."""
    out = []
    for raw, unit in NUM.findall(text):
        n = float(raw.replace(",", ""))
        out.append(n)
        low = unit.lower()
        for word, mult in WORD_PREFIX.items():
            if low.startswith(word):
                out.append(n * mult)
                break
        else:
            if len(unit) >= 2 and unit[0] in SYM_PREFIX and unit[1:].lower() in ("hz", "w", "v", "a", "h", "f"):
                out.append(n * SYM_PREFIX[unit[0]])
        if low.startswith("feet") or low.startswith("foot"):
            out.append(n * 12)  # also allow inches
    return out


def close(a, b, tol=0.05):
    a, b = abs(a), abs(b)  # answers print signs loosely ("- 43 dB")
    return b != 0 and abs(a - b) / b <= tol or a == b == 0


def solve_power(e):
    P, E, I, R = (e.get(k) for k in "PEIR")
    if P is None:
        P = E * I if E is not None and I is not None else (I * I * R if I is not None else E * E / R)
    if E is None:
        E = P / I if I is not None else math.sqrt(P * R)
    if I is None:
        I = P / E if E is not None else math.sqrt(P / R)
    if R is None:
        R = E / I
    return {"P": P, "E": E, "I": I, "R": R}[e["solve"]]


def computed(e):
    """Results the app will show; at least one must appear in the correct answer."""
    k = e["kind"]
    if k in ("ohms", "power", "reactance") and not any(x in e for x in "PEIRfLC"):
        return None  # formula only
    if k == "ohms":
        E, I, R = e.get("E"), e.get("I"), e.get("R")
        return [{"E": lambda: I * R, "I": lambda: E / R, "R": lambda: E / I}[e["solve"]]()]
    if k == "power":
        return [solve_power(e)]
    if k == "combine":
        v = e["values"]
        additive = (e["part"] == "capacitor") == (e["arrangement"] == "parallel")
        return [sum(v) if additive else 1 / sum(1 / x for x in v)]
    if k == "decibel":
        if "db" in e:
            ratio, db = 10 ** (e["db"] / 10), e["db"]
        else:
            ratio, db = e["ratio"], 10 * math.log10(e["ratio"])
        if "watts" in e:
            return [e["watts"] * ratio]
        return [ratio, db, abs(1 - ratio) * 100, ratio * 100, 1 / ratio]
    if k == "prefix":
        return [e["value"] * 10 ** (PREFIX[e["from"]] - PREFIX[e["to"]])]
    if k == "waveform":
        peak = {"peak": 1, "rms": math.sqrt(2), "pp": 0.5}[e["given"]] * e["value"]
        rms = peak / math.sqrt(2)
        return [{"peak": peak, "rms": rms, "pp": 2 * peak, "pep": rms * rms / e.get("ohms", 1)}[e["solve"]]]
    if k == "swr" and "z1" in e:
        return [max(e["z1"], e["z2"]) / min(e["z1"], e["z2"])]
    if k == "reactance":
        f, L, C = e.get("f"), e.get("L"), e.get("C")
        return [{
            "XL": lambda: 2 * math.pi * f * L,
            "XC": lambda: 1 / (2 * math.pi * f * C),
            "f0": lambda: 1 / (2 * math.pi * math.sqrt(L * C)),
        }[e["solve"]]()]
    if k == "timeconstant" and "R" in e:
        return [e["R"] * e["C"]]
    if k == "transformer":
        ratio = e["np"] / e["ns"]
        return [e["vp"] / ratio] if "vp" in e else [ratio, 1 / ratio]
    if k == "wavelength":
        if e.get("example"):
            return None  # a labelled example for a formula question
        return [300 / e["freqMHz"]] if "freqMHz" in e else [300 / e["meters"]]
    if k == "antenna" and "freqMHz" in e and e["type"] in ("dipole", "vertical"):
        feet = (468 if e["type"] == "dipole" else 234) / e["freqMHz"]
        return [feet, feet * 12]
    return None


def check_entry(e, q, problems):
    k = e.get("kind")
    say = lambda msg: problems.append(f"{e.get('q')}: {k}: {msg}")
    num = lambda key: isinstance(e.get(key), (int, float)) and not isinstance(e.get(key), bool)
    cap = e.get("caption")
    if cap is not None:
        if not isinstance(cap, str) or not cap.strip():
            say("caption must be a non-empty string")
        elif len(cap) > 90:
            say(f"caption is {len(cap)} chars (max 90)")
    for v in e.values():
        if isinstance(v, str) and re.search("[–—]", v):
            say("em/en dash found")
    check_units(e, q, say)
    if k == "bandplan":
        if e.get("band") not in BANDS:
            return say(f"unknown band {e.get('band')}")
        lo, hi = BANDS[e["band"]]
        m = e.get("mark")
        if m is not None:
            pts = m if isinstance(m, list) else [m]
            if len(pts) not in (1, 2) or not all(isinstance(x, (int, float)) for x in pts):
                return say("mark must be a number or [lo, hi]")
            if any(x < lo - 1e-9 or x > hi + 1e-9 for x in pts):
                say(f"mark {m} outside {e['band']} ({lo}-{hi} MHz)")
    elif k == "spectrum":
        if any(b not in BANDS for b in e.get("bands", [])):
            say("unknown band in bands")
        if "mark" in e and not num("mark"):
            say("mark must be a number")
    elif k == "wavelength":
        if num("freqMHz") == num("meters"):
            say("give exactly one of freqMHz or meters")
    elif k == "repeater":
        if e.get("band") not in ("6m", "2m", "1.25m", "70cm"):
            say("band must be 6m, 2m, 1.25m or 70cm")
    elif k == "modes":
        if e.get("highlight") not in MODES:
            say("unknown highlight")
    elif k == "ionosphere":
        if e.get("layer") not in ("D", "E", "F1", "F2", "F") or e.get("time") not in ("day", "night"):
            say("layer/time invalid")
    elif k == "antenna":
        if e.get("type") not in ("dipole", "vertical", "yagi", "loop"):
            say("unknown type")
        if e.get("highlight") not in (None, "reflector", "driven", "director", "radials"):
            say("unknown highlight")
    elif k == "callsign":
        if not re.fullmatch(r"[AKNW][A-Z]?\d[A-Z]{1,3}", str(e.get("call", ""))):
            say("call must look like a US call sign")
    elif k == "phonetic":
        if not re.fullmatch(r"[A-Za-z0-9 ]{1,12}", str(e.get("text", ""))):
            say("text must be letters/digits, max 12")
    elif k == "codes":
        if e.get("set") not in CODES or e.get("highlight") not in CODES.get(e.get("set"), []):
            say("set/highlight invalid")
    elif k == "ohms":
        given = [x for x in "EIR" if x != e.get("solve") and num(x)]
        if e.get("solve") not in ("E", "I", "R") or len(given) not in (0, 2) or num(e["solve"]):
            say("solve one of E/I/R from the other two (or give none to show the formula)")
    elif k == "power":
        given = [x for x in "PEIR" if num(x)]
        if e.get("solve") not in tuple("PEIR") or len(given) not in (0, 2) or e["solve"] in given:
            say("solve one of P/E/I/R from exactly two others (or give none to show the formula)")
    elif k == "combine":
        v = e.get("values")
        if e.get("part") not in ("resistor", "capacitor", "inductor") or e.get("arrangement") not in ("series", "parallel"):
            say("part/arrangement invalid")
        if not isinstance(v, list) or not 2 <= len(v) <= 6 or not all(isinstance(x, (int, float)) and x > 0 for x in v):
            say("values must be 2 to 6 positive numbers")
    elif k == "decibel":
        if num("db") == num("ratio"):
            say("give exactly one of db or ratio")
    elif k == "prefix":
        if not num("value") or e.get("from") not in PREFIX or e.get("to") not in PREFIX or e["from"] == e["to"]:
            say("value/from/to invalid")
    elif k == "waveform":
        if e.get("given") not in ("peak", "rms", "pp") or e.get("solve") not in ("peak", "rms", "pp", "pep") or not num("value"):
            say("given/solve/value invalid")
        elif e["solve"] == "pep" and not num("ohms"):
            say("pep needs ohms")
    elif k == "swr":
        if not (num("swr") or (num("z1") and num("z2"))):
            say("give swr or z1 and z2")
    elif k == "reactance":
        need = {"XL": "fL", "XC": "fC", "f0": "LC"}.get(e.get("solve"))
        if need is None or not (all(num(x) for x in need) or not any(num(x) for x in "fLC")):
            say("solve XL needs f,L; XC needs f,C; f0 needs L,C")
    elif k == "timeconstant":
        if num("R") != num("C") or e.get("show", "charge") not in ("charge", "discharge"):
            say("R and C go together; show is charge or discharge")
        if "tau" in e and e["tau"] not in (1, 2, 3, 4, 5):
            say("tau must be 1 to 5")
    elif k == "transformer":
        if not (num("np") and num("ns")):
            say("np and ns required")
    elif k == "component":
        if e.get("symbol") not in SYMBOLS:
            say("unknown symbol")
    elif k == "meter":
        if e.get("meter") not in ("voltmeter", "ammeter"):
            say("meter invalid")
    elif k == "filter":
        if e.get("type") not in ("lowpass", "highpass", "bandpass", "notch"):
            say("type invalid")
    elif k == "logic":
        if e.get("gate") not in ("AND", "OR", "NAND", "NOR", "XOR", "XNOR", "NOT"):
            say("gate invalid")
    elif k == "fact":
        if not isinstance(e.get("value"), str) or not isinstance(e.get("label"), str):
            return say("value and label must be strings")
        if e["value"] not in q["answers"][q["correct"]]:
            say(f"value {e['value']!r} not in correct answer {q['answers'][q['correct']]!r}")
        if len(e["label"]) > 60:
            say("label max 60 chars")
    else:
        return say("unknown kind")

    try:
        results = computed(e)
    except (KeyError, TypeError, ZeroDivisionError, ValueError) as ex:
        return say(f"cannot compute: {ex!r}")
    if results is not None:
        found = answer_values(q["answers"][q["correct"]])
        if not any(close(f, r) for r in results for f in found):
            say(f"computed {[round(r, 6) for r in results]} not in correct answer {q['answers'][q['correct']]!r}")


def validate(entries, pool_id):
    pool = json.loads((ASSETS / f"pools/{pool_id}.json").read_text())
    questions = {q["id"]: q for q in pool["questions"]}
    problems, seen = [], set()
    if not isinstance(entries, list):
        return ["file must be a JSON array"]
    for e in entries:
        qid = e.get("q")
        if qid not in questions:
            problems.append(f"{qid}: not a question in {pool_id}")
            continue
        if qid in seen:
            problems.append(f"{qid}: more than one aid")
        seen.add(qid)
        check_entry(e, questions[qid], problems)
    return problems


def main():
    if len(sys.argv) == 3:
        targets = [(Path(sys.argv[1]), sys.argv[2])]
    else:
        targets = [(ASSETS / f"aids/{p}.json", p) for p in POOLS]
    failed = False
    for path, pool_id in targets:
        entries = json.loads(path.read_text())
        problems = validate(entries, pool_id)
        print(f"{path.name}: {len(entries)} aids, {len(problems)} problems")
        for p in problems:
            print("  " + p)
        failed |= bool(problems)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
