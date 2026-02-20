<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" omit-xml-declaration="yes"/>
  <xsl:template match="/">
    <summary total="{count(//user)}" empty="{count(//user)=0}" />
  </xsl:template>
</xsl:stylesheet>
