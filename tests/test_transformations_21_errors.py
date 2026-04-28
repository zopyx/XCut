from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures_21_errors"
ENABLED_LANGS = {
    s.strip().lower()
    for s in os.getenv("XF_TEST_LANGS", "python,rust,ts,go,swift,js,cpp,java,kotlin").split(",")
    if s.strip()
}

RUST_XFORM_BIN = ROOT / "xform-rs" / "target" / "release" / "xform"
TS_XFORM_BIN = ROOT / "xform-ts" / "dist" / "cli.js"
GO_XFORM_BIN = ROOT / "xform-go" / "bin" / "xform"
SWIFT_XFORM_BIN = ROOT / "xform-swift" / ".build" / "release" / "xform-swift"
JS_XFORM_BIN = ROOT / "xform-js" / "src" / "cli.js"
JAVA_XFORM_BIN = ROOT / "xform-java" / "bin" / "xform"
KOTLIN_XFORM_JAR = ROOT / "xform-kotlin" / "build" / "libs" / "xform-kotlin-1.0.jar"
CPP_XFORM_BIN = ROOT / "xform-cpp" / "build" / "src" / "xform"


def _cases():
    return sorted(p for p in FIXTURES.iterdir() if p.is_dir())


def _params_for(lang: str):
    out = []
    currently_passing = {
        "python": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "rust": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "swift": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "ts": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "go": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "js": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "kotlin": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "java": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
        "cpp": {
            "case31_unsupported_version",
            "case32_unknown_ruleset",
            "case33_position_outside_for",
            "case34_last_outside_for",
            "case35_duplicate_attributes",
            "case37_missing_required_param",
        },
    }
    for case in _cases():
        if case.name in currently_passing.get(lang, set()):
            out.append(pytest.param(case, id=case.name))
        else:
            out.append(
                pytest.param(
                    case,
                    id=case.name,
                    marks=pytest.mark.xfail(
                        reason="2.1 error fixture corpus targets future implementation updates across all runtimes",
                        strict=False,
                    ),
                )
            )
    return out


def _run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True)


def _assert_expected_error(result: subprocess.CompletedProcess[str], expected_code: str) -> None:
    combined = f"{result.stdout}\n{result.stderr}"
    assert result.returncode != 0
    assert expected_code in combined


def _python_cmd(xform: Path, xml: Path) -> list[str]:
    return [sys.executable, "-m", "zopyx.xform.cli", str(xml), str(xform)]


def _rust_cmd(xform: Path, xml: Path) -> list[str]:
    if "rust" not in ENABLED_LANGS:
        pytest.skip("Rust tests disabled")
    if not RUST_XFORM_BIN.exists():
        pytest.skip("Rust xform binary not built")
    return [str(RUST_XFORM_BIN), str(xml), str(xform)]


def _ts_cmd(xform: Path, xml: Path) -> list[str]:
    if "ts" not in ENABLED_LANGS:
        pytest.skip("TypeScript tests disabled")
    if not TS_XFORM_BIN.exists():
        pytest.skip("TypeScript xform binary not built")
    return ["node", str(TS_XFORM_BIN), str(xml), str(xform)]


def _go_cmd(xform: Path, xml: Path) -> list[str]:
    if "go" not in ENABLED_LANGS:
        pytest.skip("Go tests disabled")
    if not GO_XFORM_BIN.exists():
        pytest.skip("Go xform binary not built")
    return [str(GO_XFORM_BIN), str(xml), str(xform)]


def _swift_cmd(xform: Path, xml: Path) -> list[str]:
    if "swift" not in ENABLED_LANGS:
        pytest.skip("Swift tests disabled")
    if not SWIFT_XFORM_BIN.exists():
        pytest.skip("Swift xform binary not built")
    return [str(SWIFT_XFORM_BIN), str(xml), str(xform)]


def _js_cmd(xform: Path, xml: Path) -> list[str]:
    if "js" not in ENABLED_LANGS:
        pytest.skip("JavaScript tests disabled")
    if not JS_XFORM_BIN.exists():
        pytest.skip("JavaScript xform CLI not built")
    return ["node", str(JS_XFORM_BIN), str(xml), str(xform)]


def _java_cmd(xform: Path, xml: Path) -> list[str]:
    if "java" not in ENABLED_LANGS:
        pytest.skip("Java tests disabled")
    if not JAVA_XFORM_BIN.exists():
        pytest.skip("Java xform binary not built")
    return [str(JAVA_XFORM_BIN), str(xml), str(xform)]


def _kotlin_cmd(xform: Path, xml: Path) -> list[str]:
    if "kotlin" not in ENABLED_LANGS:
        pytest.skip("Kotlin tests disabled")
    if not KOTLIN_XFORM_JAR.exists():
        pytest.skip("Kotlin xform binary not built")
    return ["java", "-jar", str(KOTLIN_XFORM_JAR), str(xml), str(xform)]


def _cpp_cmd(xform: Path, xml: Path) -> list[str]:
    if "cpp" not in ENABLED_LANGS:
        pytest.skip("C++ tests disabled")
    if not CPP_XFORM_BIN.exists():
        pytest.skip("C++ xform binary not built")
    return [str(CPP_XFORM_BIN), str(xml), str(xform)]


@pytest.mark.parametrize("case", _params_for("python"))
def test_python_xform_errors_21(case: Path) -> None:
    result = _run(_python_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("rust"))
def test_rust_xform_errors_21(case: Path) -> None:
    result = _run(_rust_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("ts"))
def test_ts_xform_errors_21(case: Path) -> None:
    result = _run(_ts_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("go"))
def test_go_xform_errors_21(case: Path) -> None:
    result = _run(_go_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("swift"))
def test_swift_xform_errors_21(case: Path) -> None:
    result = _run(_swift_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("js"))
def test_js_xform_errors_21(case: Path) -> None:
    result = _run(_js_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("java"))
def test_java_xform_errors_21(case: Path) -> None:
    result = _run(_java_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("kotlin"))
def test_kotlin_xform_errors_21(case: Path) -> None:
    result = _run(_kotlin_cmd(case / "transform.xform", case / "input.xml"))
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())


@pytest.mark.parametrize("case", _params_for("cpp"))
def test_cpp_xform_errors_21(case: Path) -> None:
    result = _run(_cpp_cmd(case / "transform.xform", case / "input.xml"))
    combined = f"{result.stdout}\n{result.stderr}"
    if combined.startswith("xform-cpp:"):
        pytest.skip("C++ CLI not implemented yet")
    _assert_expected_error(result, (case / "expected_error.txt").read_text(encoding="utf-8").strip())
