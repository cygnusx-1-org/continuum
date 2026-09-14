#!/usr/bin/env python3
"""Check localized strings against the base resources, without network access.

Run from any directory: python3 scripts/verify-translations.py
This checks resource contracts, not the linguistic quality of translations.
"""

import argparse
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


FORMAT = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?(?:[tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])"
)
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}
# Zero-width and bidi marks: invisible in the UI, and machine translation leaves them behind.
INVISIBLE = re.compile("[\u200b\u200c\u200d\u200e\u200f\u202a-\u202e\ufeff]")
# Forms used by the currently supported locales. Keep other even when integer
# counts select many (e.g. Russian); Android requires a fallback.
PLURAL_FORMS = {
    "cs": {"one", "few", "other"},
    "hr": {"one", "few", "other"},
    "pl": {"one", "few", "many", "other"},
    "ro": {"one", "few", "other"},
    "ru": {"one", "few", "many", "other"},
    "uk": {"one", "few", "many", "other"},
    "ja": {"other"},
    "ko": {"other"},
    "vi": {"other"},
    "zh": {"other"},
}


def text(element):
    """Decode the Android quoting used by these string resources."""
    value = "".join(element.itertext())
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return value.replace(r"\n", "\n").replace(r"\t", "\t").replace(r"\'", "'").replace(r'\"', '"')


def read_resources(path, errors):
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        errors.append(f"{path}: {exc}")
        return {}
    if root.tag != "resources":
        errors.append(f"{path}: expected a resources root")
    names = [element.get("name") for element in root]
    for name, count in Counter(names).items():
        if name is None or count != 1:
            errors.append(f"{path}: invalid or duplicate resource name {name!r}")
    return {element.get("name"): element for element in root}


def compare_value(source, localized, label, errors):
    expected, actual = text(source), text(localized)
    if not actual.strip():
        errors.append(f"{label}: empty translation")
    if Counter(FORMAT.findall(expected)) != Counter(FORMAT.findall(actual)):
        errors.append(f"{label}: format arguments differ from base")
    if expected.count("\n") != actual.count("\n"):
        errors.append(f"{label}: line break count differs from base")
    if re.search(r"ZXQ\d+QXZ|⟦\d+⟧", actual):
        errors.append(f"{label}: unresolved translation token")
    if INVISIBLE.search(actual) and not INVISIBLE.search(expected):
        errors.append(f"{label}: invisible zero-width or bidi character")
    if re.match(r"^[-\u2013\u2014]\s*[^\d\s]", actual) and not re.match(r"^[-\u2013\u2014]", expected):
        errors.append(f"{label}: leading dash not present in the base")
    for pattern, description in [
        (r"https?://[^\s<>]+", "URLs"),
        # Attribution lines carry bare domains, which the URL pattern above does not see.
        (r"\bwww\.[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "bare domains"),
        (r"[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "email addresses"),
        (r"\\u[0-9a-fA-F]{4}", "Unicode escapes"),
        (r"@[a-z]+/[a-zA-Z0-9_]+", "resource references"),
    ]:
        if Counter(re.findall(pattern, expected)) != Counter(re.findall(pattern, actual)):
            errors.append(f"{label}: {description} differ from base")
    expected_tags = Counter(child.tag for child in source.iter() if child is not source)
    actual_tags = Counter(child.tag for child in localized.iter() if child is not localized)
    if expected_tags != actual_tags:
        errors.append(f"{label}: markup tags differ from base")


def verify(res_dir):
    errors = []
    base = read_resources(res_dir / "values/strings.xml", errors)
    expected = {name: el for name, el in base.items() if el.get("translatable") != "false"}
    files = sorted(res_dir.glob("values-*/strings.xml"))
    if not files:
        errors.append(f"{res_dir}: no locale strings.xml files found")
    for path in files:
        localized = read_resources(path, errors)
        label = path.parent.name
        for name in sorted(expected.keys() - localized.keys()):
            errors.append(f"{label}/{name}: missing translation")
        for name in sorted(localized.keys() - expected.keys(), key=str):
            errors.append(f"{label}/{name}: extra or non-translatable resource")
        if list(localized) != list(expected):
            errors.append(f"{label}: resources do not follow base ordering")
        for name in expected.keys() & localized.keys():
            source, target = expected[name], localized[name]
            resource = f"{label}/{name}"
            if source.tag != target.tag:
                errors.append(f"{resource}: resource type differs from base")
                continue
            if target.get("translatable") == "false":
                errors.append(f"{resource}: translation incorrectly marked non-translatable")
            if source.tag == "plurals":
                forms = [child.get("quantity") for child in target]
                lang = label.split("-")[1]
                required = PLURAL_FORMS.get(lang, {"one", "other"})
                if len(set(forms)) != len(forms) or not set(forms) <= QUANTITIES:
                    errors.append(f"{resource}: invalid or duplicate plural categories")
                if not required <= set(forms):
                    errors.append(f"{resource}: missing plural categories {sorted(required - set(forms))}")
                source_forms = {child.get("quantity"): child for child in source}
                for child in target:
                    quantity = child.get("quantity")
                    original = source_forms.get(quantity)
                    if original is None:
                        original = source_forms["other"]
                    compare_value(original, child, f"{resource}/{quantity}", errors)
            else:
                compare_value(source, target, resource, errors)
                # These examples are parser input, so punctuation is literal.
                examples = {
                    "post_filter_limits_summary": ("100-5000", "100-", "0-5000"),
                    "post_filter_limit_needs_lower_bound": ("0-5000",),
                    # The text says XXXXX must not be changed, so it must be five X.
                    "uploaded_images_explanation": ("XXXXX",),
                    "select_giphy_gif_explanation": ("XXXXX",),
                }
                for example in examples.get(name, ()):
                    if example not in text(target):
                        errors.append(f"{resource}: missing literal input example {example!r}")
                if name.endswith("_explanation") and name in examples:
                    runs = Counter(re.findall(r"X{2,}", text(target)))
                    if set(runs) - {"XXXXX"}:
                        errors.append(f"{resource}: XXXXX token has the wrong length")
    return files, expected, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--res-dir", type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/res",
    )
    args = parser.parse_args()
    files, expected, errors = verify(args.res_dir)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        print(f"FAILED: {len(errors)} translation resource issues", file=sys.stderr)
        return 1
    print(f"PASS: {len(files)} locales, {len(expected)} translatable resources per locale")
    return 0


if __name__ == "__main__":
    sys.exit(main())
