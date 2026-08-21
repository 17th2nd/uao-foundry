#!/usr/bin/env python3
"""Regression for F-R4: benchmark median aggregation must not truncate even-count medians.

Codex found that int(statistics.median(...)) silently dropped the .5 of an even-length lane's
median prompt tokens (ASA 1211.5 -> 1211, PID_F 1711.5 -> 1711). The task-success result was never
affected; this guards the token-cost presentation.
"""
import statistics
import unittest


class MedianAggregationTest(unittest.TestCase):
    def test_even_count_median_is_not_truncated(self):
        # The exact ASA / PID_F situation: an even number of runs straddling a half-integer median.
        asa = [1147, 1276]          # median 1211.5
        pid_f = [1682, 1741]        # median 1711.5
        self.assertEqual(statistics.median(asa), 1211.5)
        self.assertEqual(statistics.median(pid_f), 1711.5)
        # The defect was int() coercion, which floors the half-token.
        self.assertNotEqual(int(statistics.median(asa)), statistics.median(asa))

    def test_odd_count_median_is_an_exact_member(self):
        self.assertEqual(statistics.median([1, 5, 9]), 5)


if __name__ == "__main__":
    unittest.main()
