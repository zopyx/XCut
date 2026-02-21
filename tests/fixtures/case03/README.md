# case03

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Emits a single <summary> with total=count(//user) and empty=true/false depending on whether any users exist.
