#!/usr/bin/env python3
"""Run the real JVM before/after rankers, then report their fixed-corpus accuracy."""
import argparse
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--from-report', action='store_true', help='Read the most recent JVM output instead of rerunning tests')
    parser.add_argument('--json', type=pathlib.Path)
    args = parser.parse_args()
    if not args.from_report:
        command = ['cmd.exe','/d','/c',str(ROOT/'gradlew.bat')] if __import__('os').name == 'nt' else [str(ROOT/'gradlew')]
        subprocess.run(command + [':app:testDebugUnitTest','--tests','*ReleaseAccuracyTest','--rerun'], cwd=ROOT, check=True)
    report = ROOT/'app/build/test-results/testDebugUnitTest/TEST-app.shobdo.keyboard.banglish.ReleaseAccuracyTest.xml'
    suite = ET.parse(report).getroot()
    assert suite.attrib['failures'] == '0', 'benchmark test failed'
    output = suite.findtext('system-out') or ''
    match = re.search(r'n=(\d+): BEFORE top-1=(\d+) top-3=(\d+); AFTER top-1=(\d+) top-3=(\d+)',output)
    assert match, 'no actual benchmark measurements in JVM report'
    n,b1,b3,a1,a3 = map(int,match.groups())
    result = dict(cases=n, before=dict(top1=b1,top3=b3), after=dict(top1=a1,top3=a3),
        source='Latest JVM test report' if args.from_report else 'Fresh JVM benchmark',
        method='Same fixed fixtures, NFC comparisons, visible top-3 including Roman chip; no user learning or context')
    for label in ['before','after']:
        print(f'{label.upper()}: top-1 {result[label]["top1"]}/{n} ({100*result[label]["top1"]/n:.2f}%), top-3 {result[label]["top3"]}/{n} ({100*result[label]["top3"]/n:.2f}%)')
    print(next(line for line in output.splitlines() if line.startswith('RELEASE LOOKUP')))
    if args.json: args.json.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    return 0

if __name__ == '__main__': raise SystemExit(main())
