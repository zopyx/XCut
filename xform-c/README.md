# XForm C Implementation

This is a C implementation of the XForm transformation language.

## Requirements

- C11 compiler (gcc or clang)
- CMake 3.10 or later
- libxml2 development files
- pkg-config

## Building

```bash
cmake -S . -B build
cmake --build build
```

Or from the project root:

```bash
make build-c
```

## Usage

```bash
./build/src/xform <input.xml> <transform.xform>
```

## Implementation Status

The C implementation supports:

- XPath-like path expressions (/, //, ., .., @, predicates)
- Element constructors
- Control flow: for, if-then-else, let-in, match
- Function definitions and calls
- Pattern matching for rules
- Built-in functions:
  - string(), number(), boolean(), typeOf()
  - name(), attr(), text(), children(), elements(), copy()
  - count(), empty(), head(), tail(), last(), position()
  - concat(), seq(), sum(), sort(), distinct()

Limitations:
- Some advanced functions like groupBy(), lookup(), index() are not implemented
- Parent axis is not supported
- Unicode support is basic

## Testing

Run tests from the project root:

```bash
XF_TEST_LANGS=c make test-python
```

Or with pytest directly:

```bash
XF_TEST_LANGS=c uv run python -m pytest tests/test_transformations.py -k c_xform
```
