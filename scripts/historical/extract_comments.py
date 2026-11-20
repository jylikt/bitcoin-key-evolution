import json
import re
import sys
from pathlib import Path

PATTERN = re.compile(
    r"\b(todo|fixme|delme|not sure|are we sure|should|shouldn't|could|couldn't|"
    r"would|wouldn't|perhaps|probably|maybe|perhaps|want|need|wish|hope|plan|"
    r"future|later|for now|currently|not implemented|isn't implemented|yet|"
    r"what do we|how do we|why|warning|careful|not safe|unsafe|not recommended|"
    r"assume|assumption|supposed|intended|because|reason|designed|prevent|avoid|"
    r"limit|protect|safer|easier|harder|difficult|problem|issue|wrong|redundant|"
    r"wiseguy|hate|never go|i'd|i'm|we're|we've|we can|we could|we should|"
    r"we need|we want|we have|we don't|we didn't|we are|we were|you |"
    r"doesn't|don't|can't|cannot|hasn't|haven't|isn't|aren't|wasn't|weren't)\b",
    re.I,
)
EXCLUDE = re.compile(r"copyright|distributed under|file license|^\s*[\s/*=-]*$", re.I)


def extract(text):
    records = []
    i = 0
    line = 1
    state = "code"
    quote = None
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "code":
            if c == "/" and n == "/":
                start = i
                start_line = line
                i += 2
                body_start = i
                while i < len(text) and text[i] != "\n":
                    i += 1
                records.append({"start": start, "end": i, "line": start_line, "end_line": line,
                                "text": text[body_start:i]})
                continue
            if c == "/" and n == "*":
                start = i
                start_line = line
                i += 2
                body_start = i
                while i + 1 < len(text) and text[i:i + 2] != "*/":
                    if text[i] == "\n":
                        line += 1
                    i += 1
                records.append({"start": start, "end": i + 2, "line": start_line, "end_line": line,
                                "text": text[body_start:i]})
                i = min(i + 2, len(text))
                continue
            if c in ('"', "'"):
                state = "string"
                quote = c
        elif state == "string":
            if c == "\\":
                i += 1
            elif c == quote:
                state = "code"
        if c == "\n":
            line += 1
        i += 1
    return records


def group_comments(text, records):
    groups = []
    for record in records:
        if groups:
            previous = groups[-1]
            between = text[previous["end"]:record["start"]]
            if previous["end_line"] + 1 == record["line"] and not between.strip():
                previous["text"] += "\n" + record["text"]
                previous["end"] = record["end"]
                previous["end_line"] = record["end_line"]
                continue
        groups.append(record.copy())
    return groups


def clean(text):
    lines = []
    for line in text.splitlines():
        line = re.sub(r"^\s*\*+\s?", "", line)
        lines.append(line.strip())
    return "\n".join(lines).strip()


def main():
    root = Path(sys.argv[1])
    output = Path(sys.argv[2])
    count = 0
    candidates = 0
    with output.open("w", encoding="utf-8") as out:
        for path in sorted(root.rglob("*")):
            if path.suffix.lower() not in (".cpp", ".h", ".c"):
                continue
            text = path.read_text(encoding="cp1252")
            for record in group_comments(text, extract(text)):
                body = clean(record["text"])
                if not body or EXCLUDE.search(body):
                    continue
                item = {"file": path.relative_to(root).as_posix(), "line": record["line"],
                        "end_line": record["end_line"], "text": body}
                item["candidate"] = bool(PATTERN.search(body))
                out.write(json.dumps(item, ensure_ascii=False) + "\n")
                count += 1
                candidates += item["candidate"]
    print(json.dumps({"comments": count, "candidates": candidates, "output": str(output)}))


if __name__ == "__main__":
    main()
