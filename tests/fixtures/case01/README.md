# case01

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Converts a catalog of items into a feed of entries, carrying item @id to entry/@id, name to title, and numeric price to a price element with currency=EUR.
