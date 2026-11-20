import datetime
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent
REPO = ROOT / "bitcoin-git"
VERSIONS = [
    "nov08", "0.1.0", "0.1.3", "0.1.5", "0.2.0", "0.2.2", "0.2.4", "0.2.5",
    "0.2.6", "0.2.7", "0.2.8", "0.2.9", "0.2.10", "0.2.11", "0.2.12", "0.2.13",
    "0.3.0", "0.3.1", "0.3.2", "0.3.3", "0.3.6", "0.3.7", "0.3.8", "0.3.10",
    "0.3.12", "0.3.13", "0.3.14", "0.3.15", "0.3.17", "0.3.18", "0.3.19",
]
DATES = {
    "nov08": "2008-11-15", "0.1.0": "2009-01-09", "0.1.3": "2009-01-12T22:48:23Z",
    "0.1.5": "2009-02-04T19:46:04Z",
}


def norm(text):
    return re.sub(r"\s+", " ", text).strip()


def git(*args):
    return subprocess.check_output(["git", "-C", str(REPO), *args], text=True).strip()


def blame(version, file, line):
    tag = "v" + version
    paths = [file, "src/" + file]
    path = next((p for p in paths if subprocess.run(["git", "-C", str(REPO), "cat-file", "-e", f"{tag}:{p}"], capture_output=True).returncode == 0), None)
    if path is None:
        return {}
    result = git("blame", "--line-porcelain", "-L", f"{line},{line}", tag, "--", path)
    lines = result.splitlines()
    item = {"commit": lines[0].split()[0]}
    for entry in lines:
        if entry.startswith("author "):
            item["author"] = entry[7:]
        elif entry.startswith("author-time "):
            timestamp = int(entry[12:])
            item["author_date"] = datetime.datetime.fromtimestamp(timestamp, datetime.timezone.utc).isoformat().replace("+00:00", "Z")
        elif entry.startswith("summary "):
            item["summary"] = entry[8:]
    return item


def main():
    records = {}
    version_meta = []
    for index, version in enumerate(VERSIONS):
        if version.startswith("0.") and version not in DATES:
            info = git("log", "-1", "--format=%H%x09%aI%x09%an", "v" + version).split("\t")
            date = info[1]
        else:
            date = DATES.get(version, "")
        version_meta.append({"version": version, "date": date})
        current = set()
        for line in (ROOT / "comments" / f"{version}.jsonl").read_text().splitlines():
            item = json.loads(line)
            if not item["candidate"]:
                continue
            key = norm(item["text"])
            current.add(key)
            if key not in records:
                records[key] = {"text": item["text"], "normalized": key, "first_version": version,
                                "first_index": index, "first_date": date, "locations": [],
                                "seen_versions": []}
            record = records[key]
            record["last_version"] = version
            record["last_index"] = index
            record["seen_versions"].append(version)
            record["locations"].append({"version": version, "file": item["file"], "line": item["line"],
                                        "end_line": item["end_line"]})
        for record in records.values():
            if record["first_index"] < index and record["seen_versions"][-1] != version:
                record.setdefault("gaps", []).append(version)
        print(version, "candidates", len(current), "unique total", len(records), flush=True)
    for number, record in enumerate(records.values(), 1):
        version = record["first_version"]
        if version in DATES:
            record["author"] = "Satoshi Nakamoto (archive)"
        else:
            location = record["locations"][0]
            record.update(blame(version, location["file"], location["line"]))
        print("blame", number, len(records), record["first_version"], record.get("author", "?"), flush=True)
    output = {"versions": version_meta, "comments": list(records.values())}
    (ROOT / "comment-timeline.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
