<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <labels>
      <xsl:for-each select="//product">
        <label id="{string(@id)}">
          <xsl:choose>
            <xsl:when test="number(price) &gt; 20">expensive</xsl:when>
            <xsl:otherwise>cheap</xsl:otherwise>
          </xsl:choose>
        </label>
      </xsl:for-each>
    </labels>
  </xsl:template>
</xsl:stylesheet>
