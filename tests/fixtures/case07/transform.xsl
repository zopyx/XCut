<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <root>
      <xsl:copy-of select="//section"/>
    </root>
  </xsl:template>
</xsl:stylesheet>
