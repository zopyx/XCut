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
	# Auto-detect JAVA_HOME if not set
	DETECTED_JAVA_HOME=""; \
	if [ -z "$${JAVA_HOME}" ]; then \
		for d in /Library/Java/JavaVirtualMachines/graalvm-ce-java17-*/Contents/Home \
		         /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
		         /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
		         /usr/lib/jvm/java-17-* \
		         /usr/lib/jvm/temurin-17-*; do \
			if [ -d "$${d}" ]; then DETECTED_JAVA_HOME="$${d}"; break; fi; \
		done; \
	fi; \
	if [ -n "$${DETECTED_JAVA_HOME}" ]; then export JAVA_HOME="$${DETECTED_JAVA_HOME}"; fi; \
	if [ -n "$${JAVA_HOME}" ]; then \
		"$${JAVA_HOME}/bin/javac" -version >/dev/null 2>&1; \
		if [ $$? -ne 0 ]; then \
			echo "Skipping Java build (javac not found in JAVA_HOME=$${JAVA_HOME})"; \
			exit 0; \
		fi; \
	else \
		if ! command -v javac >/dev/null 2>&1; then \
			echo "Skipping Java build (javac not available)"; \
			exit 0; \
		fi; \
		javac -version 2>&1 | grep -q 'javac 1[7-9]\|javac [2-9][0-9]'; \
		if [ $$? -ne 0 ]; then \
			echo "Skipping Java build (Java 17+ required, found: $$(javac -version 2>&1))"; \
			exit 0; \
		fi; \
	fi; \
	mkdir -p xform-java/build/classes xform-java/bin; \
	if [ -n "$${JAVA_HOME}" ]; then \
		"$${JAVA_HOME}/bin/javac" -d xform-java/build/classes $$(find xform-java/src/main/java -name '*.java'); \
	else \
		javac -d xform-java/build/classes $$(find xform-java/src/main/java -name '*.java'); \
	fi
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
