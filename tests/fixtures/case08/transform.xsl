<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <out>
      <xsl:for-each select="/*/*">
        <xsl:choose>
          <xsl:when test="self::a"><A><xsl:value-of select="."/></A></xsl:when>
          <xsl:when test="self::b"><B><xsl:value-of select="."/></B></xsl:when>
          <xsl:otherwise><Other><xsl:value-of select="string(.)"/></Other></xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
    </out>
  </xsl:template>
</xsl:stylesheet>
