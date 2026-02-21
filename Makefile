.PHONY: test test-python test-rust build build-rust

build: build-rust

build-rust:
	cd xform-rs && cargo build --release

test: test-python test-rust

test-python: build-rust
	uv sync --extra dev
	uv run python -m pytest tests/ -v

test-rust:
	cd xform-rs && cargo test
