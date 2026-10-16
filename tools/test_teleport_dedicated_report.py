#!/usr/bin/env python3
"""Pure log-evidence regressions; never start Java, Gradle, Minecraft or a server."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("teleport_dedicated", Path(__file__).with_name("test-teleport-dedicated.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CompletionEvidenceTests(unittest.TestCase):
    def warmup(self, result, requested=100, full=100, entities=100, platforms=3):
        for line in ["FAMILY_TP_WARMUP_REGISTERED requested=100",
                     "FAMILY_TP_WARMUP_DISPATCHER_ACTIVE server=DedicatedServer",
                     f"FAMILY_TP_WARMUP_READY requested={requested}, issued={requested}, full={full}, "
                     f"entityData={entities}, initialCold=75, platforms={platforms}, dispatcherCycles=90"]:
            result.feed(line)

    def evidence(self, sites=range(13), shutdown=True, native_pass=True):
        result = runner.ValidationEvidence({f"scenario{site}": site for site in range(13)})
        self.warmup(result)
        for site in sites:
            result.feed(f"FAMILY_TP_TEST_START name=scenario{site}, timeoutTicks=500")
            result.feed(f"FAMILY_TELEPORT_RUNTIME_COMPLETE site={site}")
            if native_pass:
                result.feed(f"FAMILY_TP_TEST_PASS name=scenario{site}")
        if shutdown:
            result.feed("[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server")
            result.feed("[Server thread/INFO] [minecraft/MinecraftServer]: All dimensions are saved")
        return result

    def test_all_thirteen_native_passes_and_normal_shutdown_are_accepted(self):
        self.assertEqual([], self.evidence().failures(0, True))

    def test_ready_fixture_and_passing_summary_cannot_replace_missing_scenario(self):
        result = self.evidence(sites=range(12))
        result.feed("All required tests passed :)")
        self.assertTrue(result.failures(0, True))

    def test_completion_marker_before_then_succeed_is_not_a_native_pass(self):
        result = self.evidence(sites=[0], native_pass=False)
        self.assertFalse(result.suite_complete())
        self.assertEqual(set(), result.passed_tests)

    def test_watchdog_after_passing_summary_invalidates_zero_exit(self):
        result = self.evidence()
        result.feed("ServerHangWatchdog detected that a single server tick took 60.00 seconds")
        self.assertIn("watchdog/server crash reported", result.failures(0, True))

    def test_duplicate_runtime_completion_and_native_pass_are_rejected(self):
        for extra in ["FAMILY_TELEPORT_RUNTIME_COMPLETE site=0", "FAMILY_TP_TEST_PASS name=scenario0"]:
            result = self.evidence();result.feed(extra)
            self.assertTrue(result.failures(0, True))

    def test_missing_shutdown_nonzero_exit_and_unsent_stop_are_rejected(self):
        self.assertTrue(self.evidence(shutdown=False).failures(0, True))
        self.assertTrue(self.evidence().failures(1, True))
        self.assertTrue(self.evidence().failures(0, False))

    def test_incomplete_full_entity_data_and_platforms_are_rejected(self):
        for counts in [{"full": 99}, {"entities": 99}, {"platforms": 2}, {"requested": 110}]:
            result = runner.ValidationEvidence({"probe": 12})
            self.warmup(result, **counts)
            self.assertFalse(result.warmup_ready)
            self.assertTrue(result.errors)

    def test_inactive_dispatcher_cannot_report_ready(self):
        result = runner.ValidationEvidence({"probe": 12})
        result.feed("FAMILY_TP_WARMUP_REGISTERED requested=100")
        result.feed("FAMILY_TP_WARMUP_READY requested=100, issued=100, full=100, entityData=100, initialCold=75, platforms=3, dispatcherCycles=90")
        self.assertTrue(result.errors)
        self.assertFalse(result.warmup_ready)

    def test_scenario_before_warmup_and_concurrent_start_are_rejected(self):
        result = runner.ValidationEvidence({"first": 0, "second": 1})
        result.feed("FAMILY_TP_TEST_START name=first")
        self.assertTrue(result.errors)
        result = runner.ValidationEvidence({"first": 0, "second": 1})
        self.warmup(result)
        result.feed("FAMILY_TP_TEST_START name=first")
        result.feed("FAMILY_TP_TEST_START name=second")
        self.assertTrue(result.errors)

    def test_late_fixture_failure_invalidates_completed_suite(self):
        result = self.evidence()
        result.feed("FAMILY_TP_INTEGRATION_FAILED phase=cleanup")
        self.assertFalse(result.suite_complete())

    def test_smoke_scope_requires_its_native_probe_and_shutdown(self):
        result = runner.ValidationEvidence({"freshserverdispatchesandcompletesfixturequeue": 12})
        self.warmup(result)
        result.feed("FAMILY_TP_TEST_START name=freshserverdispatchesandcompletesfixturequeue")
        result.feed("FAMILY_TELEPORT_RUNTIME_COMPLETE site=12")
        self.assertFalse(result.suite_complete())
        result.feed("FAMILY_TP_TEST_PASS name=freshserverdispatchesandcompletesfixturequeue")
        self.assertTrue(result.suite_complete())
        self.assertTrue(result.failures(0, True))  # successful probe still needs normal shutdown


if __name__ == "__main__":
    unittest.main()
