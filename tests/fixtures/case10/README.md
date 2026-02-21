# case10

This testcase runs the XSLT (`transform.xsl`) and XForm (`transform.xform`) against `input.xml` to produce `expected.xml`.

- Input: `input.xml`
- Transformations: `transform.xsl`, `transform.xform`
- Output: `expected.xml`
- Behavior: Outputs <flags> with value 'both' when any <flag> and any <missing> element exist; otherwise 'not-both'.
