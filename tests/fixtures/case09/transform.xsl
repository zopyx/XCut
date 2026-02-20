<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <expensive>
      <xsl:for-each select="//item[number(price) &gt; 10]">
        <name><xsl:value-of select="name"/></name>
      </xsl:for-each>
    </expensive>
  </xsl:template>
</xsl:stylesheet>
