"""Surface test failures in check annotations as well as detailed job logs."""
from pathlib import Path
import xml.etree.ElementTree as ET


def report(message):
    message = message[:3500].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Android verification failed::{message}")


for path in Path("app/build/test-results").rglob("TEST-*.xml"):
    for test in ET.parse(path).getroot().iter("testcase"):
        for tag in ("failure", "error"):
            failure = test.find(tag)
            if failure is not None:
                report(f"{test.get('classname')}.{test.get('name')}: {failure.get('message', '')}\n{failure.text or ''}")
for path in Path("app/build/reports").glob("lint-results-*.xml"):
    for issue in ET.parse(path).getroot().findall("issue"):
        if issue.get("severity") in ("Error", "Fatal"):
            report(f"Lint {issue.get('id')}: {issue.get('message')}")
