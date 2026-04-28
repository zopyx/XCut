.PHONY: test test-python test-rust test-kotlin test-java test-cpp test-c build build-rust build-ts build-go build-swift build-js build-cpp build-c build-java build-kotlin

build: build-rust build-ts build-go build-swift build-js build-cpp build-c build-java build-kotlin

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

build-java:
	if ! command -v javac >/dev/null 2>&1; then \
		echo "Skipping Java build (javac not available)"; \
		exit 0; \
	fi
	mkdir -p xform-java/build/classes xform-java/bin
	javac -d xform-java/build/classes $$(find xform-java/src/main/java -name '*.java')
	@echo '#!/usr/bin/env sh' > xform-java/bin/xform
	@echo 'if [ -n "$${JAVA_HOME}" ]; then' >> xform-java/bin/xform
	@echo '  exec "$${JAVA_HOME}/bin/java" -cp "$$(dirname "$$0")/../build/classes" zopyx.xform.Main "$$@"' >> xform-java/bin/xform
	@echo 'else' >> xform-java/bin/xform
	@echo '  exec java -cp "$$(dirname "$$0")/../build/classes" zopyx.xform.Main "$$@"' >> xform-java/bin/xform
	@echo 'fi' >> xform-java/bin/xform
	chmod +x xform-java/bin/xform

build-kotlin:
	cd xform-kotlin && ./gradlew build

test: test-python test-rust test-kotlin

test-python: build-rust build-ts build-go build-swift build-js build-cpp build-c build-java build-kotlin
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	langs=python,rust,ts,go,js,cpp,c; \
	if [ -x xform-swift/.build/release/xform-swift ]; then \
		langs=$$langs,swift; \
	else \
		echo "Swift binary not built; excluding Swift tests"; \
	fi; \
	if [ -x xform-java/bin/xform ]; then \
		langs=$$langs,java; \
	else \
		echo "Java binary not built; excluding Java tests"; \
	fi; \
	if [ -f xform-kotlin/build/libs/xform-kotlin-1.0.jar ]; then \
		langs=$$langs,kotlin; \
	else \
		echo "Kotlin binary not built; excluding Kotlin tests"; \
	fi; \
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=$$langs uv run python -m pytest tests/ -v

test-rust:
	cd xform-rs && cargo test

test-java: build-java
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=java uv run python -m pytest tests/test_transformations.py -v -k java_xform

test-c: build-c
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=c uv run python -m pytest tests/test_transformations.py -v -k c_xform

test-cpp: build-cpp
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=cpp uv run python -m pytest tests/test_transformations.py -v -k cpp_xform

test-kotlin: build-kotlin
	UV_CACHE_DIR=/tmp/uv-cache uv sync --extra dev
	UV_CACHE_DIR=/tmp/uv-cache XF_TEST_LANGS=kotlin uv run python -m pytest tests/test_transformations.py -v -k kotlin_xform
