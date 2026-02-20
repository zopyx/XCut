.PHONY: test test-python test-rust

test: test-python test-rust

test-python:
	uv sync --extra dev
	uv run python -m pytest tests/ -v

test-rust:
	cd xform-rs && cargo test
