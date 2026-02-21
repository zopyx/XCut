# case11

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Extracts all indexterm elements, sorts them by primary then secondary, and outputs primary/secondary/tertiary term elements for each.
