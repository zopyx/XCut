<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <tags>
      <xsl:for-each select="//tag[not(. = preceding::tag)]">
        <xsl:sort select="."/>
        <tag><xsl:value-of select="."/></tag>
      </xsl:for-each>
    </tags>
  </xsl:template>
</xsl:stylesheet>
