# case12

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Performs an identity-style copy of the entire document by building a result tree fragment and converting it with exsl:node-set.
