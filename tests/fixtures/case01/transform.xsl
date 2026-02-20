<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>

  <xsl:template match="/">
    <feed>
      <xsl:for-each select="//item">
        <entry id="{string(@id)}">
          <title><xsl:value-of select="name"/></title>
          <price currency="EUR"><xsl:value-of select="number(price)"/></price>
        </entry>
      </xsl:for-each>
    </feed>
  </xsl:template>
</xsl:stylesheet>
