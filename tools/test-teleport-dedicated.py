#!/usr/bin/env python3
"""Warm a fresh disposable dedicated world, then run 13 native teleport GameTests sequentially.
External developer tool: invoking it runs Gradle. No real client/socket handshake is simulated.
"""
import datetime
import fcntl
import os
from pathlib import Path
import re
import queue
import threading
import subprocess
import sys
import time


class ValidationEvidence:
    """Require ready warmup, ordered native GameTest passes, and clean shutdown."""
    def __init__(self, expected_tests):
        self.expected_tests = dict(expected_tests)  # native name -> unique scenario site
        self.finished_sites = set()
        self.passed_tests = set()
        self.started_tests = set()
        self.active_test = None
        self.warmup_ready = False
        self.warmup_registered = False
        self.dispatcher_active = False
        self.last_progress = "no progress record"
        self.shutdown_started = False
        self.dimensions_saved = False
        self.errors = set()

    def feed(self, line):
        if "FAMILY_TP_WARMUP_REGISTERED " in line:
            self.warmup_registered = True
        if "FAMILY_TP_WARMUP_DISPATCHER_ACTIVE " in line:
            self.dispatcher_active = True
        if "FAMILY_TP_WARMUP_PROGRESS " in line:
            self.last_progress = line.strip()
        if "FAMILY_TP_WARMUP_READY " in line:
            counts = dict((key, int(value)) for key, value in re.findall(
                r"(requested|issued|full|entityData|platforms|initialCold|dispatcherCycles)=([0-9]+)", line))
            requested = counts.get("requested", 0)
            if (self.warmup_ready or not self.warmup_registered or not self.dispatcher_active
                    or not 100 <= requested <= 109
                    or any(counts.get(key) != requested for key in ("issued", "full", "entityData"))
                    or counts.get("platforms") != 3 or counts.get("initialCold", 0) <= 0
                    or counts.get("dispatcherCycles", 0) <= 1):
                self.errors.add("invalid/duplicate fixture readiness evidence")
            else:
                self.warmup_ready = True
        match = re.search(r"FAMILY_TP_TEST_START name=(\w+)\b", line)
        if match:
            name = match[1]
            if (not self.warmup_ready or self.active_test is not None
                    or name not in self.expected_tests or name in self.started_tests):
                self.errors.add(f"unexpected/duplicate/concurrent scenario start={name}")
            self.active_test = name
            self.started_tests.add(name)
        match = re.search(r"FAMILY_TELEPORT_RUNTIME_COMPLETE site=([0-9]+)\b", line)
        if match:
            site = int(match[1])
            if site in self.finished_sites or self.expected_tests.get(self.active_test) != site:
                self.errors.add(f"duplicate/unexpected runtime completion site={site}")
            self.finished_sites.add(site)
        match = re.search(r"FAMILY_TP_TEST_PASS name=(\w+)\b", line)
        if match:
            name = match[1]
            if (name != self.active_test or name in self.passed_tests
                    or name not in self.expected_tests
                    or self.expected_tests.get(name) not in self.finished_sites):
                self.errors.add(f"native pass lacks matching scenario completion={name}")
            self.passed_tests.add(name)
            self.active_test = None
        if "FAMILY_TP_INTEGRATION_FAILED" in line or "required tests failed" in line:
            self.errors.add("fixture/runtime integration failed")
        if re.search(r"ServerHangWatchdog|single server tick took|Considering it to be crashed|Preparing crash report|crash report has been saved", line, re.I):
            self.errors.add("watchdog/server crash reported")
        if "[minecraft/MinecraftServer]: Stopping server" in line:
            self.shutdown_started = True
        if "All dimensions are saved" in line:
            self.dimensions_saved = True

    def suite_complete(self):
        return (self.warmup_ready and self.passed_tests == set(self.expected_tests)
                and self.finished_sites == set(self.expected_tests.values())
                and self.active_test is None and not self.errors)

    def failures(self, exit_code, stop_sent):
        failures = sorted(self.errors)
        if not self.suite_complete():
            failures.append(f"incomplete integration: registered={self.warmup_registered}, dispatcherActive={self.dispatcher_active}, warmupReady={self.warmup_ready}, "
                            f"missingTests={sorted(set(self.expected_tests) - self.passed_tests)}, "
                            f"missingSites={sorted(set(self.expected_tests.values()) - self.finished_sites)}")
        if not stop_sent or not self.shutdown_started or not self.dimensions_saved:
            failures.append("normal stop/save completion was not observed")
        if exit_code != 0:
            failures.append(f"Gradle/server exit={exit_code}")
        return failures


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--smoke", action="store_true", help="Fresh cold warmup + native readiness probe only; not full runtime acceptance")
    options = parser.parse_args()
    if os.environ.get("DUDUNKA_TEST_ACCEPT_EULA") != "1":
        sys.exit("Read https://aka.ms/MinecraftEULA; set DUDUNKA_TEST_ACCEPT_EULA=1 if you agree to run this disposable test server.")
    root = Path(__file__).resolve().parents[1]
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
    version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    if not re.search(r'version "17(?:[.+"]|$)', version.stderr + version.stdout):
        sys.exit("JDK 17 is required; set JAVA_HOME.")
    reports = root / "build/reports/dudunka-tests"
    reports.mkdir(parents=True, exist_ok=True)
    with (reports / ".runner.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            sys.exit("Another tools/test.sh or dedicated validation run is active.")
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + f"-{os.getpid()}"
        report = reports / ("teleport-dedicated-" + stamp)
        work = root / "run" / ("dudunka-teleport-server-" + stamp)
        report.mkdir(); work.mkdir(parents=True)
        (work / "eula.txt").write_text("eula=true\n")
        (work / ".dudunka-teleport-fixture").write_text("disposable-teleport-validation\n")
        (work / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=world\n"
            "view-distance=2\nsimulation-distance=2\nenable-command-block=true\n"
            "allow-flight=true\nspawn-protection=0\nlevel-seed=-255494441983840131\n"
        )
        init = report / "dedicated.gradle"
        init.write_text("""allprojects { afterEvaluate {
    minecraft.runs.server.property 'forge.enableGameTest', 'true'
    minecraft.runs.server.property 'forge.enabledGameTestNamespaces', 'dudunka_journey'
    minecraft.runs.server.property 'dudunka.debugFamilyTeleport', 'true'
    minecraft.runs.server.property 'dudunka.testTeleportFixtures', 'true'
} }
gradle.taskGraph.whenReady { graph ->
    def task = graph.allTasks.find { it.name == 'runServer' }
    if (task == null) throw new GradleException('runServer was not found')
    task.doFirst {
        def requested = task.project.findProperty('dudunkaTeleportServerWorld')
        if (!(requested instanceof String) || !(requested ==~ /run\\/dudunka-teleport-server-[A-Za-z0-9_-]+/))
            throw new GradleException('Invalid fresh dedicated-server path')
        def dir = task.project.file(requested)
        if (!dir.isDirectory() || new File(dir, 'world/level.dat').exists())
            throw new GradleException('A new unused dedicated-server directory is required')
        task.runConfig.get().workingDirectory(dir)
        // Forward this runner's console pipe to DedicatedServer.handleConsoleInputs.
        task.standardInput = System.in
    }
}
""")
        args = [str(root / "gradlew"), "--console=plain", "-Dnet.minecraftforge.gradle.check.certs=false"]
        if os.environ.get("DUDUNKA_TEST_ONLINE") != "1":
            args.append("--offline")
        args += ["-I", str(init), f"-PdudunkaTeleportServerWorld={work.relative_to(root)}", "runServer", "-x", "downloadAssets"]
        runtime_source = (root / "src/main/java/io/github/golovinss/dudunka/test/FamilyTeleportRuntimeTests.java").read_text()
        names = re.findall(r"@GameTest\([^\n]*\)\s+public static void (\w+)\(GameTestHelper h\)", runtime_source)
        sites = [int(site) for site in re.findall(r"new Trip\(h,\s*([0-9]+),", runtime_source)]
        if len(names) != 13 or len(sites) != 13 or set(sites) != set(range(13)) or len(set(names)) != 13:
            sys.exit("All original 12 scenarios plus cold fixture probe must be present exactly once.")
        expected_tests = dict(zip((name.lower() for name in names), sites))
        if options.smoke:
            expected_tests = {name: site for name, site in expected_tests.items() if site == 12}
        expected = len(expected_tests)
        evidence = ValidationEvidence(expected_tests)
        remaining = iter(expected_tests)
        scope = "fixture-smoke" if options.smoke else "runtime-13"
        print(f"Dedicated console validation; logs: {report}; fresh world: {work}", flush=True)
        passed = False
        reason = "server exited without a completed suite"
        with (report / "dedicated.log").open("w") as log:
            process = subprocess.Popen(args, cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, bufsize=1)
            output = queue.Queue()
            def read_output():
                for line in process.stdout:
                    log.write(line); log.flush(); output.put(line)
                output.put(None)
            reader = threading.Thread(target=read_output, daemon=True)
            reader.start()
            started = time.monotonic(); sent = False; stop_sent = False
            warmup_started = None; awaiting = None
            def command(text):
                process.stdin.write(text + "\n"); process.stdin.flush()
            try:
                while time.monotonic() - started < 1200:
                    if warmup_started is not None and not evidence.warmup_ready and time.monotonic() - warmup_started > 200:
                        reason = (f"fixture warmup deadline exceeded: registered={evidence.warmup_registered}, "
                                  f"dispatcherActive={evidence.dispatcher_active}; {evidence.last_progress}")
                        break
                    try:
                        line = output.get(timeout=1)
                    except queue.Empty:
                        continue
                    if line is None:
                        break
                    evidence.feed(line)
                    if not sent and re.search(r'Done \([\d.,]+s\)!', line):
                        for text in ["gamerule doMobSpawning false", "gamerule doWeatherCycle false",
                                     "gamerule doDaylightCycle false", "time set day", "dudunka_test_teleport prepare"]:
                            command(text)
                        sent = True
                        warmup_started = time.monotonic()
                        print("Console started bounded asynchronous fixture warmup; GameTest clocks have not started.", flush=True)
                    if awaiting in evidence.passed_tests:
                        awaiting = None
                    if evidence.errors:
                        reason = "; ".join(sorted(evidence.errors)); break
                    if evidence.suite_complete():
                        passed = sent
                        reason = f"completed {scope}: warmup and all {expected} native GameTests"
                        break
                    if evidence.warmup_ready and awaiting is None:
                        name = next(remaining, None)
                        if name is not None:
                            command("dudunka_test_teleport run " + name)
                            awaiting = name
                            print("Running native 500-tick GameTest: " + name, flush=True)
                else:
                    reason = "dedicated validation exceeded 20-minute deadline"
            except KeyboardInterrupt:
                reason = "interrupted by developer"
            except (BrokenPipeError, OSError) as failure:
                reason = f"server console/output failed: {failure}"
            finally:
                if process.poll() is None:
                    try:
                        command("stop")
                        stop_sent = True
                        process.wait(timeout=60)
                    except (BrokenPipeError, subprocess.TimeoutExpired):
                        process.terminate()
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            process.kill(); process.wait()
                        passed = False
                        reason += "; server did not stop normally"
                reader.join(timeout=10)
                if reader.is_alive():
                    passed = False
                    reason += "; output stream did not close"
                # The reader drains during wait, so shutdown cannot fill the pipe.
                # Inspect ALL queued tail output: a crash after a passing summary
                # must invalidate the result even if a launcher exits zero.
                while not output.empty():
                    tail = output.get_nowait()
                    if tail is not None:
                        evidence.feed(tail)
        failures = evidence.failures(process.returncode, stop_sent)
        passed = passed and not failures
        if failures:
            reason += "; " + "; ".join(failures)
        result = "PASS" if passed else "FAIL"
        (report / "summary.txt").write_text(
            f"dedicated={result}\nreason={reason}\nexit={process.returncode}\n"
            f"scope={scope}\nruntime_test_count={expected}\nreport={report}\nfresh_world={work}\n"
            f"runtime_completed_sites={sorted(evidence.finished_sites)}\n"
            f"native_passed_tests={sorted(evidence.passed_tests)}\nwarmup_ready={evidence.warmup_ready}\n"
            f"dispatcher_active={evidence.dispatcher_active}\nlast_warmup_progress={evidence.last_progress}\n"
            f"normal_shutdown={stop_sent and evidence.shutdown_started and evidence.dimensions_saved}\n"
            "networked_client_acceptance=NOT RUN\nmanual_minecraft_bmc4_acceptance=NOT RUN\n"
        )
        print(f"{result}: {reason}. Report: {report / 'summary.txt'}", flush=True)
        return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
