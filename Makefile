.PHONY: test test-python test-rust build build-rust build-ts build-go build-swift

build: build-rust build-ts build-go build-swift

build-rust:
	cd xform-rs && cargo build --release

build-ts:
	cd xform-ts && if [ ! -d node_modules ]; then echo "node_modules missing in xform-ts; run npm install"; exit 1; fi && npm run build

build-go:
	cd xform-go && mkdir -p bin && go build -o bin/xform ./cmd/xform

build-swift:
	cd xform-swift && \
		if [ ! -w "$$HOME/.cache" ] || [ ! -w "$$HOME/Library/Caches" ]; then \
			echo "Skipping swift build (cache directories not writable)"; \
			exit 0; \
		fi && \
		swift build -c release -Xcc -fmodules-cache-path=/tmp/xform-swift-clang-cache

test: test-python test-rust

test-python: build-rust build-ts build-go build-swift
	uv sync --extra dev
	uv run python -m pytest tests/ -v

test-rust:
	cd xform-rs && cargo test
