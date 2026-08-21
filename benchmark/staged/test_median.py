#!/usr/bin/env python3
"""Regression for F-R4: the benchmark ANALYSERS must not truncate even-count medians.

Codex's closure ratification correctly rejected an earlier version of this test that exercised
``statistics.median`` itself — it would have passed even if the analysers reintroduced int()
truncation. These tests call the real analyser aggregation, so a regression to int() coercion in
either analyser fails here.
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))

from analyse_lanes import median_prompt_tokens  # noqa: E402
from analyse_staged import lane_summary  # noqa: E402

# The exact even-count situation Codex flagged: ASA 1211.5, PID_F 1711.5.
ASA_ROWS = [
    {"lane": "ASA", "task": "T01", "correct": True, "exact_files": [], "prompt_tokens": 1147},
    {"lane": "ASA", "task": "T03", "correct": False, "exact_files": [], "prompt_tokens": 1276},
]
PID_F_ROWS = [
    {"lane": "PID_F", "task": "T04", "correct": True, "exact_files": [], "prompt_tokens": 1682},
    {"lane": "PID_F", "task": "T08", "correct": False, "exact_files": [], "prompt_tokens": 1741},
]
TRUTHS = {t: {"relevant_files": []} for t in ("T01", "T03", "T04", "T08")}


class AnalyserMedianTest(unittest.TestCase):
    def test_analyse_lanes_median_is_not_truncated(self):
        self.assertEqual(median_prompt_tokens(ASA_ROWS), 1211.5)
        self.assertEqual(median_prompt_tokens(PID_F_ROWS), 1711.5)

    def test_analyse_staged_lane_summary_median_is_not_truncated(self):
        self.assertEqual(lane_summary(ASA_ROWS, TRUTHS, "ASA")["medianPromptTokens"], 1211.5)
        self.assertEqual(lane_summary(PID_F_ROWS, TRUTHS, "PID_F")["medianPromptTokens"], 1711.5)

    def test_reintroduced_truncation_would_fail_here(self):
        # Guard the guard: a floored median is a different, detectable value.
        self.assertNotEqual(int(median_prompt_tokens(PID_F_ROWS)), median_prompt_tokens(PID_F_ROWS))


if __name__ == "__main__":
    unittest.main()
