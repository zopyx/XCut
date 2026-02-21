<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="xml" omit-xml-declaration="yes" />

  <xsl:template match="/">
    <summary>
      <title><xsl:value-of select="string(//article-title[1])" /></title>
      <journal><xsl:value-of select="string(//journal-title[1])" /></journal>
      <year><xsl:value-of select="string(//pub-date[1]/year)" /></year>
      <authors>
        <xsl:attribute name="count">
          <xsl:value-of select="count(//contrib[@contrib-type='author'])" />
        </xsl:attribute>
        <xsl:for-each select="//contrib[@contrib-type='author']">
          <xsl:variable name="given" select="string(name/given-names)" />
          <xsl:variable name="surname" select="string(name/surname)" />
          <author>
            <xsl:choose>
              <xsl:when test="string-length($given) &gt; 0 and string-length($surname) &gt; 0">
                <xsl:value-of select="concat($given, ' ', $surname)" />
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="concat($given, $surname)" />
              </xsl:otherwise>
            </xsl:choose>
          </author>
        </xsl:for-each>
      </authors>
      <metrics>
        <xsl:attribute name="sections"><xsl:value-of select="count(//sec)" /></xsl:attribute>
        <xsl:attribute name="paragraphs"><xsl:value-of select="count(//p)" /></xsl:attribute>
        <xsl:attribute name="figures"><xsl:value-of select="count(//fig)" /></xsl:attribute>
        <xsl:attribute name="tables"><xsl:value-of select="count(//table-wrap)" /></xsl:attribute>
        <xsl:attribute name="refs"><xsl:value-of select="count(//ref-list//ref)" /></xsl:attribute>
        <xsl:attribute name="floats"><xsl:value-of select="count(//floats-group)" /></xsl:attribute>
      </metrics>
    </summary>
  </xsl:template>
</xsl:stylesheet>
