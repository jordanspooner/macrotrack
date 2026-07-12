#!/usr/bin/env python3
"""
OCR label extractor.

Reads the noisy ML-Kit-style OCR .txt dumps in ocr_preview/ and extracts,
per 100g and per serving:
    energy (kJ), energy (kcal), fat (g), carbohydrate (g), protein (g)
plus the serving size in grams.

Approach (per the agreed design):
  * Pre-normalise OCR noise (kJ/kcal variants, comma decimals, etc.).
  * Fuzzy-match keyword tokens (infix + small edit distance), e.g. "fat",
    "carbohydrate", "protein", "energy", "saturates", "sugar" ...
  * For a simple macro keyword, the first number scanned immediately after is
    the per-100g value; the second number is the per-serving value. If the
    keyword appears a second time, the first number after that instance is used
    as the per-serving fallback.  Scanning stops at the next keyword/kJ/kcal.
  * The REAL nutrition-label occurrence is preferred over incidental words
    ("for", "at", "protein:" in marketing text) using a match-strength score.
  * Energy is special: attach a number to the LEFT of a kJ/kcal token first,
    else to the RIGHT; a "header" pair of units followed by values is handled;
    missing kJ/kcal is derived from the other via the 4.184 factor, and an
    internal consistency check corrects the obvious mistakes.
  * Finally, repair impossible values using magnitude rules (missing decimal
    points, O/0, Z/7, a/q/g units, ...) and an Atwater energy-consistency
    pass (9*fat + 4*carb + 4*protein ~= kcal).

Run:  python3 ocr_extract.py  [ocr_dir]  [ground_truth.json]
"""

import json
import os
import re
import sys

# --------------------------------------------------------------------------
# Configuration / bounds (per 100g physical limits used for repair)
# --------------------------------------------------------------------------
PER100_BOUNDS = {
    "kj": 4200,      # pure-fat 100g ~ 3770 kJ
    "kcal": 1100,    # pure-fat 100g ~ 900 kcal (allow a little slack)
    "fat": 100,      # cannot exceed 100 g per 100 g
    "carbs": 100,    # "
    "protein": 100,  # practical ceiling for food (~90)
}
# per-serving bounds are looser (a big serving can approach the per-100 max)
PER_SERVING_BOUNDS = {
    "kj": 4200, "kcal": 1100, "fat": 100, "carbs": 100, "protein": 100,
}

# Keyword definitions. Variants are tried as infix (strong) or, for short
# tokens, by edit distance (weak).  'serving' is only used as a STOP word /
# serving-context hint, not extracted as a macro.
KEYWORDS = {
    "energy":      ["energy"],
    "fat":         ["fat"],
    "saturates":   ["saturates", "saturated", "satur"],
    "carbohydrate":["carbohydrate", "carb", "carbo"],
    "sugar":       ["sugars", "sugar"],
    "protein":     ["protein", "prot", "pro"],
    "fibre":       ["fibre", "fiber", "fibres"],
    "salt":        ["salt", "sel"],
    "kj":          ["kj"],
    "kcal":        ["kcal"],
    "serving":     ["serving", "portion", "slice", "tablespoon", "teaspoon"],
    "vitamin":     ["vitamin"],
    "ingredient":  ["ingredients", "ingredient"],
    "reference":   ["reference", "intake"],
}

# Keywords that, if encountered while scanning forward for numbers, immediately
# stop the scan (we don't want to grab numbers belonging to the next row).
STOP_KEYWORDS = {
    "energy", "fat", "saturates", "carbohydrate", "sugar", "protein",
    "fibre", "salt", "kj", "kcal", "serving", "vitamin", "ingredient",
    "reference", "of", "which", "from", "per",
}


def norm_text(text: str) -> str:
    """Lower-case and collapse OCR noise for unit tokens."""
    t = text.lower()
    t = re.sub(r"[kK][iI1j3]", " kj ", t)        # KI, k3, k1, kJ, ki ...
    t = re.sub(r"[kK]/", " kj ", t)              # "K/"  "k/"
    t = re.sub(r"[kK][cC][aAeE][lLkK]", " kcal ", t)  # kcal, kcak, kcaK ...
    t = t.replace(",", ".")                     # comma decimal separator
    # a few diacritics that creep in
    for a, b in [("ı", "i"), ("ä", "a"), ("ö", "o"), ("ü", "u"),
                 ("ç", "c"), ("&", " "), ("ô", "o")]:
        t = t.replace(a, b)
    return t


def tokenize(text: str):
    """Split into whitespace/punctuation tokens, keeping fused number+letter
    words intact (e.g. '30.8a', '166q', '1908k3')."""
    toks = re.split(r"\s+", text)
    out = []
    for tk in toks:
        for p in re.split(r"[:|/(){}\[\]%=]", tk):
            p = p.strip()
            if p:
                out.append(p)
    return out


def _edit_distance(a: str, b: str) -> int:
    m, n = len(a), len(b)
    dp = list(range(n + 1))
    for i in range(1, m + 1):
        prev = dp[0]
        dp[0] = i
        for j in range(1, n + 1):
            cur = dp[j]
            cost = 0 if a[i - 1] == b[j - 1] else 1
            dp[j] = min(dp[j] + 1, dp[j - 1] + 1, prev + cost)
            prev = cur
    return dp[n]


def classify_token(tok: str):
    """Return (canonical_keyword, strength).

    Strength is high for an infix/long-variant match and low for a loose
    edit-distance match.  Short keywords (len <= 2) require an EXACT token
    match (so 'for'/'at' never match 'ft'); medium keywords (3-4 chars) match
    by infix only (so 'at'/'for'/'not'/'lot' never match 'fat'); only LONG
    tokens may use edit distance (so corrupted long words like
    'carbohytrate' still match 'carbohydrate').
    """
    t = tok.lower().strip(".,;:-")
    if not t:
        return None, -1
    best, best_s = None, -1
    for canon, variants in KEYWORDS.items():
        for v in variants:
            if v in t:
                s = 10 + len(v)                       # strong (infix) match
            elif (len(t) >= 5 and len(v) >= 5
                  and _edit_distance(t, v) <= 2):
                s = len(v)                           # long edit-distance match
            else:
                continue
            if s > best_s:
                best_s, best = s, canon
    return best, best_s


def kw(tok: str):
    """Just the canonical keyword (or None) for stop-word checks."""
    return classify_token(tok)[0]


# --------------------------------------------------------------------------
# Number cleaning / OCR repair
# --------------------------------------------------------------------------
UNIT_LETTERS = set("agqçkjcrsxzo")  # trailing/leading unit-ish letters to drop
# Whole-token letter-only numbers (OCR dropped the digits entirely).
LETTER_NUMBER_TOKENS = {
    "ia": 1.0, "l0": 1.0, "z0": 7.0, "z": 7.0,
    "og": 0.0, "o": 0.0, "0g": 0.0, "0": 0.0,
    "nil": 0.0, "trace": 0.0, "n/a": 0.0, "na": 0.0, "none": 0.0, "-": 0.0,
}
# Confusable letters that may stand in for a digit, ONLY when adjacent to a
# real digit (so a bare 'o' in "for" is left alone).
CONFUSABLE_ADJ = {"Z": "7", "z": "7", "O": "0", "o": "0",
                  "l": "1", "I": "1", "|": "1"}


def _adjacent_to_digit(s: str, ch: str) -> bool:
    i = s.index(ch)
    if i > 0 and s[i - 1].isdigit():
        return True
    if i + 1 < len(s) and s[i + 1].isdigit():
        return True
    return False


def clean_number(tok: str):
    """Extract a float from a noisy token. Returns
    (value, had_decimal, is_zero, tok)."""
    raw = tok
    low = raw.lower().strip()
    if low in LETTER_NUMBER_TOKENS:
        v = LETTER_NUMBER_TOKENS[low]
        return v, (v != int(v)), (v == 0.0), tok

    has_digit = any(c.isdigit() for c in raw)
    digits = []
    saw_dot = False
    for ch in raw:
        if ch.isdigit():
            digits.append(ch)
        elif ch in ".,":
            if not saw_dot:
                digits.append(".")
                saw_dot = True
        elif has_digit and ch in CONFUSABLE_ADJ and _adjacent_to_digit(raw, ch):
            digits.append(CONFUSABLE_ADJ[ch])
        elif ch.lower() in UNIT_LETTERS:
            continue
        else:
            continue
    if not digits:
        return None, False, False, tok
    numstr = "".join(digits)
    if numstr.count(".") > 1:
        first = numstr.find(".")
        numstr = numstr[:first + 1] + numstr[first + 1:].replace(".", "")
    try:
        val = float(numstr)
    except ValueError:
        return None, False, False, tok
    return val, saw_dot, False, tok


def repair(value, had_decimal, is_zero, bound, original_tok):
    """Fix impossible values using the magnitude rules."""
    if value is None:
        return None
    if is_zero:
        return 0.0
    stripped = original_tok.lower().lstrip("<>=~")
    # Leading '0' with no decimal => lost decimal (e.g. '03' -> 0.3).
    if (not had_decimal) and re.match(r"^0\d", stripped):
        return value / 10.0
    # Value exceeds the physical bound => missing decimal point.
    if not had_decimal and value > bound:
        cand = value
        for _ in range(3):
            cand /= 10.0
            if cand <= bound:
                return cand
        return cand
    return value


# --------------------------------------------------------------------------
# Scanning
# --------------------------------------------------------------------------
def find_keyword_positions(tokens):
    """Return dict canonical -> list of (index, strength)."""
    pos = {k: [] for k in KEYWORDS}
    for i, tk in enumerate(tokens):
        c, s = classify_token(tk)
        if c:
            pos[c].append((i, s))
    return pos


def scan_numbers_after(tokens, start_idx, max_nums=6, stopwords=STOP_KEYWORDS):
    """From start_idx, collect number tokens until a stop keyword is hit."""
    nums = []
    i = start_idx + 1
    while i < len(tokens) and len(nums) < max_nums:
        tk = tokens[i]
        if kw(tk) in stopwords:
            break
        r = clean_number(tk)
        i += 1
        if r is None:
            continue
        val, had_dot, is_zero, _ = r
        if val is not None:
            nums.append((val, had_dot, is_zero, tk))
    return nums


def extract_macro(tokens, pos, key, bound_per100, bound_per):
    """
    Return (per100_candidates, perServing_candidates).
    per100_candidates: list of repaired per-100g candidate values, ordered by
        match strength then position (best first). Each candidate comes from
        the first number after a keyword occurrence.
    perServing_candidates: likewise for the per-serving value (second number
        after the same occurrence, or first number after a second occurrence).
    """
    occ = [(i, s) for (i, s) in pos.get(key, []) if scan_numbers_after(tokens, i)]
    # order by strength desc, then position asc
    occ.sort(key=lambda x: (-x[1], x[0]))

    per100, per_serv = [], []
    for idx, _ in occ:
        nums = scan_numbers_after(tokens, idx)
        if not nums:
            continue
        v, hd, iz, tok = nums[0]
        per100.append(repair(v, hd, iz, bound_per100, tok))
        if len(nums) > 1:
            v2, hd2, iz2, tok2 = nums[1]
            per_serv.append(repair(v2, hd2, iz2, bound_per, tok2))
    # per-serving fallback from a second occurrence's first number
    if not per_serv and len(occ) > 1:
        nums2 = scan_numbers_after(tokens, occ[1][0])
        if nums2:
            v, hd, iz, tok = nums2[0]
            per_serv.append(repair(v, hd, iz, bound_per, tok))
    return per100, per_serv


def select_best(candidates, kcal, others, bound):
    """
    Pick the candidate per-100g value that makes the Atwater sum
    (9*fat + 4*carb + 4*protein) closest to the label's kcal, falling back to
    the smallest plausible candidate when kcal is unknown.
    `others` is a dict of the *other* macros' currently-selected per-100g
    values (used to compute the joint Atwater sum).
    """
    if not candidates:
        return None
    if kcal is None:
        # no energy anchor: prefer the smallest non-crazy candidate
        ok = [c for c in candidates if c is not None and 0 <= c <= bound]
        return min(ok) if ok else candidates[0]
    best, best_err = None, 1e18
    for c in candidates:
        if c is None:
            continue
        atm = 9 * c + 4 * others["carb"] + 4 * others["protein"] if "fat" in others or True else 0
        # build the full triple using this candidate for its own macro
        triple = dict(others)
        triple["_self"] = c
        err = abs(atwater_sum(others, self_key=_self_for(others), self_val=c) - kcal)
        if err < best_err:
            best_err, best = err, c
    return best


def _self_for(others):
    # determine which macro key is missing (the one being selected)
    for k in ("fat", "carbs", "protein"):
        if k not in others:
            return k
    return "fat"


def atwater_sum(others, self_key=None, self_val=None):
    f = others.get("fat", 0.0) if self_key != "fat" else (self_val or 0.0)
    c = others.get("carbs", 0.0) if self_key != "carbs" else (self_val or 0.0)
    p = others.get("protein", 0.0) if self_key != "protein" else (self_val or 0.0)
    return 9.0 * f + 4.0 * c + 4.0 * p


def extract_energy(tokens, pos):
    """
    Energy: attach a number to the LEFT of a kJ/kcal token first, else to the
    RIGHT (nearest unattached).  Handle the "header" pattern where the units
    are listed together and the values follow.  Then derive any missing
    kJ/kcal from the other via the 4.184 factor and an internal
    consistency check.
    """
    kj_idx = [i for (i, _s) in pos.get("kj", [])]
    kcal_idx = [i for (i, _s) in pos.get("kcal", [])]
    used = set()

    def try_attach(idx):
        # left
        if idx - 1 >= 0 and (idx - 1) not in used:
            r = clean_number(tokens[idx - 1])
            if r and r[0] is not None and kw(tokens[idx - 1]) is None:
                used.add(idx - 1)
                return r[0]
        # right (nearest unattached number)
        j = idx + 1
        while j < len(tokens) and j not in used:
            if kw(tokens[j]) in ("kj", "kcal"):
                break
            r = clean_number(tokens[j])
            if r and r[0] is not None and kw(tokens[j]) is None:
                used.add(j)
                return r[0]
            if kw(tokens[j]) is not None:
                break
            j += 1
        return None

    all_kj, all_kcal = [], []
    for idx in kj_idx:
        v = try_attach(idx)
        if v is not None:
            all_kj.append(v)
    for idx in kcal_idx:
        v = try_attach(idx)
        if v is not None:
            all_kcal.append(v)

    # Header fallback: any unit still without a number, and whose sibling unit
    # is adjacent, gets the values that follow the unit *pair* in order.
    def fill_header(unit_idx_list, kind):
        out = []
        # group consecutive unattached unit indices
        unattached = [i for i in unit_idx_list if i not in used]
        i = 0
        while i < len(unattached):
            grp = [unattached[i]]
            j = i + 1
            while j < len(unattached) and unattached[j] - grp[-1] <= 3:
                grp.append(unattached[j])
                j += 1
            # gather numbers after the group
            nums = []
            k = grp[-1] + 1
            while k < len(tokens) and len(nums) < 6:
                if kw(tokens[k]) in ("kj", "kcal"):
                    break
                r = clean_number(tokens[k])
                if r and r[0] is not None and kw(tokens[k]) is None:
                    nums.append(r[0])
                elif kw(tokens[k]) is not None:
                    break
                k += 1
            for n in nums:
                out.append(n)
                used.add(grp[0])  # mark so it isn't re-used
            i = j
        return out

    if len(all_kj) < len(kj_idx):
        all_kj += fill_header(kj_idx, "kj")
    if len(all_kcal) < len(kcal_idx):
        all_kcal += fill_header(kcal_idx, "kcal")

    kj_100 = all_kj[0] if all_kj else None
    kj_serv = all_kj[1] if len(all_kj) > 1 else None
    kcal_100 = all_kcal[0] if all_kcal else None
    kcal_serv = all_kcal[1] if len(all_kcal) > 1 else None

    # derive / consistency-correct
    def fix_pair(a, b):
        if a is not None and b is not None:
            # if they are wildly inconsistent (factor > ~6), trust the one
            # that is the smaller (kcal) and rebuild the other
            if b > 0 and a / 4.184 > b * 6:
                a = b * 4.184
            elif a > 0 and b / (a / 4.184) > 6:
                b = a / 4.184
        if a is None and b is not None:
            a = b * 4.184
        if b is None and a is not None:
            b = a / 4.184
        return a, b

    kj_100, kcal_100 = fix_pair(kj_100, kcal_100)
    kj_serv, kcal_serv = fix_pair(kj_serv, kcal_serv)
    return kj_100, kcal_100, kj_serv, kcal_serv


# --------------------------------------------------------------------------
# Serving size extraction
# --------------------------------------------------------------------------
def extract_serving_size(text):
    low = text.lower()
    has_per100 = bool(re.search(r"per\s*100\s*(g|ml)", low)) or "100g" in low \
        or "100 ml" in low or "100ml" in low
    cands = []
    for m in re.finditer(r"per\s*([0-9]+(?:\.[0-9]+)?)\s*(g|ml|m[l]?)", low):
        val = float(m.group(1))
        if abs(val - 100) > 1e-6:
            cands.append(val)
    if cands:
        for val in cands:
            if abs(val - 100) > 1e-6:
                return val
    m = re.search(r"(?:serving|slice|portion|tablespoon|teaspoon|spray)\D{0,15}?([0-9]+(?:\.[0-9]+)?)\s*g", low)
    if m:
        return float(m.group(1))
    m = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*g\s*(?:serving|slice|portion)", low)
    if m:
        return float(m.group(1))
    return None


# --------------------------------------------------------------------------
# Atwater consistency repair on the chosen per-100g macros
# --------------------------------------------------------------------------
def atwater_repair(fat, carb, protein, kcal):
    """If the Atwater sum of the macros greatly exceeds the label kcal,
    divide the dominant contributor(s) by 10 until consistent."""
    if kcal is None:
        return fat, carb, protein
    vals = {"fat": fat, "carbs": carb, "protein": protein}
    for _ in range(4):
        atm = 9 * vals["fat"] + 4 * vals["carbs"] + 4 * vals["protein"]
        if atm <= kcal * 1.5:
            break
        # find largest contributor
        contribs = {"fat": 9 * vals["fat"], "carbs": 4 * vals["carbs"],
                    "protein": 4 * vals["protein"]}
        worst = max(contribs, key=contribs.get)
        if contribs[worst] < 1.0:
            break
        vals[worst] = vals[worst] / 10.0
    return vals["fat"], vals["carbs"], vals["protein"]


# --------------------------------------------------------------------------
# Top-level
# --------------------------------------------------------------------------
def parse_file(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()
    norm = norm_text(text)
    tokens = tokenize(norm)
    pos = find_keyword_positions(tokens)

    kj_100, kcal_100, kj_serv, kcal_serv = extract_energy(tokens, pos)

    fat_c, fat_s = extract_macro(tokens, pos, "fat",
                                 PER100_BOUNDS["fat"], PER_SERVING_BOUNDS["fat"])
    carb_c, carb_s = extract_macro(tokens, pos, "carbohydrate",
                                   PER100_BOUNDS["carbs"], PER_SERVING_BOUNDS["carbs"])
    prot_c, prot_s = extract_macro(tokens, pos, "protein",
                                   PER100_BOUNDS["protein"], PER_SERVING_BOUNDS["protein"])
    serving = extract_serving_size(text)

    # pick best per-100g macro via Atwater consistency
    sel = {}
    for key, cands in (("fat", fat_c), ("carbs", carb_c), ("protein", prot_c)):
        # iterative: build others progressively
        others = {k: sel[k] for k in ("fat", "carbs", "protein") if k in sel}
        if key not in others:
            others[key] = 0.0  # placeholder so atwater_sum works
        best = select_best(cands, kcal_100, {k: sel.get(k, 0.0) for k in ("fat", "carbs", "protein") if k != key},
                           PER100_BOUNDS[key])
        sel[key] = best

    fat_100 = sel["fat"]
    carb_100 = sel["carbs"]
    prot_100 = sel["protein"]
    fat_100, carb_100, prot_100 = atwater_repair(fat_100, carb_100, prot_100, kcal_100)

    # per-serving: take best candidate, then cross-validate against per-100g
    def pick_serving(cands, per100):
        if not cands:
            return None
        # prefer the candidate closest to per100 * serving/100
        if per100 is not None and serving:
            expected = per100 * (serving / 100.0)
            best, berr = None, 1e18
            for c in cands:
                if c is None:
                    continue
                err = abs(c - expected)
                if err < berr:
                    berr, best = err, c
            return best
        return cands[0]

    fat_serv = pick_serving(fat_s, fat_100)
    carb_serv = pick_serving(carb_s, carb_100)
    prot_serv = pick_serving(prot_s, prot_100)

    # derive per-serving energy from serving size when missing
    if kj_serv is None and kj_100 is not None and serving:
        kj_serv = kj_100 * serving / 100.0
    if kcal_serv is None and kcal_100 is not None and serving:
        kcal_serv = kcal_100 * serving / 100.0

    return {
        "servingSizeG": serving,
        "per100": {"kj": kj_100, "kcal": kcal_100,
                   "fat": fat_100, "carbs": carb_100, "protein": prot_100},
        "perServing": {"kj": kj_serv, "kcal": kcal_serv,
                       "fat": fat_serv, "carbs": carb_serv, "protein": prot_serv},
    }


def load_truth(json_path):
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return {row["filename"]: row for row in data}


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    default_ocr = "/home/jordan/Projects/MacroTrack/app/src/test/resources/ocr_preview"
    ocr_dir = sys.argv[1] if len(sys.argv) > 1 else default_ocr
    truth_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(base), "sample-labels", "nutrition_labels.json")

    truth = load_truth(truth_path)
    total = correct = 0
    per_field = {}

    print("=" * 104)
    print(f"{'file':32} {'field':8} {'parsed':>10} {'expected':>10}  ok")
    print("=" * 104)

    for fname in sorted(truth.keys()):
        txt = os.path.join(ocr_dir, fname.replace(".jpg", ".txt"))
        if not os.path.exists(txt):
            print(f"{fname:32}  (no ocr txt found)")
            continue
        res = parse_file(txt)
        gt = truth[fname]
        print(f"\n{fname}  serving={res['servingSizeG']} (gt={gt.get('servingSizeG')})")
        for key, gtkey in [("kcal", "kcalPer100g"), ("fat", "fatPer100g"),
                           ("carbs", "carbsPer100g"), ("protein", "proteinPer100g")]:
            p = res["per100"].get(key)
            e = gt.get(gtkey)
            status = "MISS"
            total += 1
            if p is not None and e is not None:
                ok = abs(p - e) <= max(1.0, 0.15 * abs(e))
                if ok:
                    correct += 1
                    status = "ok"
                else:
                    status = "XX"
            per_field.setdefault(key, [0, 0])
            per_field[key][1] += 1
            if status == "ok":
                per_field[key][0] += 1
            print(f"  {key:8} {str(round(p, 3) if p is not None else None):>10} "
                  f"{str(e):>10}  {status}")

    print("\n" + "=" * 104)
    print(f"Overall per-100g macro accuracy: {correct}/{total} "
          f"({100*correct/total:.0f}%)")
    for key, (c, t) in per_field.items():
        print(f"  {key:8}: {c}/{t}")
    print("=" * 104)


if __name__ == "__main__":
    main()
