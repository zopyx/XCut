<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <flags>
      <xsl:choose>
        <xsl:when test="//flag and //missing">both</xsl:when>
        <xsl:otherwise>not-both</xsl:otherwise>
      </xsl:choose>
    </flags>
  </xsl:template>
</xsl:stylesheet>
