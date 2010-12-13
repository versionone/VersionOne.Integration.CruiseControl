<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/TR/html4/strict.dtd">
    <xsl:output method="html"/>
    <xsl:template match="/">
        <xsl:apply-templates select="//coverage[count(packages/package) != 0]" />					
    </xsl:template>
    
    <xsl:template match="coverage">
           Summary:
        <table class="section-table" cellpadding="2" cellspacing="0" border="0" width="98%">
            <th>
                <td>Package Name</td>
                <td>Class Count</td>
                <td>Line Coverage</td>
                <td>Branch Coverage</td>
                <td>Complexity</td>
            </th>
            <tr>
                <td><bold>All Packages</bold></td>
                <td><xsl:value-of select="count(packages/package/classes/class)"/></td>
                <td><xsl:value-of select="round(@line-rate *100)"/>%</td>
                <td><xsl:value-of select="round(@branch-rate*100)"/>%</td>
                <td></td>
            </tr>
            <xsl:apply-templates select="packages/package"></xsl:apply-templates>
        </table>
        <a href="c:\Autobuild$\VersionOne.Java.APIClient.source\coverage\html\index.html">Details</a>
    </xsl:template>

    <xsl:template match="package">
        <tr>
            <td><xsl:value-of select="@name"/></td>
            <td><xsl:value-of select="count(classes/class)"/></td>
            <td><xsl:value-of select="round(@line-rate *100)"/>%</td>
            <td><xsl:value-of select="round(@branch-rate*100)"/>%</td>
            <td><xsl:value-of select="substring(@complexity, 0, 6)"/></td>
        </tr>
    </xsl:template>
</xsl:stylesheet>
