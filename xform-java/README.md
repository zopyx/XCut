# xform-java

Java CLI implementation for XForm.

Current version is a Java command-line frontend that invokes the Python XForm engine (`zopyx.xform.cli`).
This keeps behavior aligned with the reference implementation while establishing the Java target/build/test wiring.

## Build

```bash
make build-java
```

## Usage

```bash
xform-java/bin/xform <input.xml> <transform.xform>
```
