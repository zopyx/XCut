<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <names>
      <xsl:for-each select="/*/*">
        <n><xsl:value-of select="name()"/></n>
      </xsl:for-each>
    </names>
  </xsl:template>
</xsl:stylesheet>
