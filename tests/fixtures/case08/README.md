# case08

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Maps each child of the root: <a> to <A>, <b> to <B>, and any other element to <Other>, preserving text content.
