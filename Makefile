.PHONY: test test-python test-rust test-cpp test-c build build-rust build-ts build-go build-swift build-js build-cpp build-c

build: build-rust build-ts build-go build-swift build-js build-cpp build-c

build-rust:
	cd xform-rs && cargo build --release

build-ts:
	cd xform-ts && if [ ! -d node_modules ]; then echo "node_modules missing in xform-ts; run npm install"; exit 1; fi && npm run build

build-go:
	cd xform-go && mkdir -p bin && go build -o bin/xform ./cmd/xform

build-swift:
	cd xform-swift && \
		cache_dir="$$HOME/.cache"; \
		if [ "$$(uname -s)" = "Darwin" ]; then cache_dir="$$HOME/Library/Caches"; fi; \
		if [ ! -d "$$cache_dir" ] || [ ! -w "$$cache_dir" ]; then \
			echo "Skipping swift build (cache directory not writable: $$cache_dir)"; \
			exit 0; \
		fi && \
		swift build -c release -Xcc -fmodules-cache-path=/tmp/xform-swift-clang-cache

build-cpp:
	cmake -S xform-cpp -B xform-cpp/build
	cmake --build xform-cpp/build

build-c:
	cmake -S xform-c -B xform-c/build
	cmake --build xform-c/build

test: test-python test-rust

test-python: build-rust build-ts build-go build-swift build-js build-cpp build-c
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	if [ -x xform-swift/.build/release/xform-swift ]; then \
		UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=python,rust,ts,go,swift,js,cpp,c uv run python -m pytest tests/ -v; \
	else \
		echo "Swift binary not built; excluding Swift tests"; \
		UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=python,rust,ts,go,js,cpp,c uv run python -m pytest tests/ -v; \
	fi

test-rust:
	cd xform-rs && cargo test

test-c: build-c
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=c uv run python -m pytest tests/test_transformations.py -v -k c_xform

test-cpp: build-cpp
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=cpp uv run python -m pytest tests/test_transformations.py -v -k cpp_xform
