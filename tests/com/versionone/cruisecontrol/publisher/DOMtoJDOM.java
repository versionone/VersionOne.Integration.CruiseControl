package com.versionone.cruisecontrol.publisher;

import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.xml.sax.InputSource;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;
import org.jdom.input.DOMBuilder;

import java.io.IOException;

public class DOMtoJDOM
{
    // DOM tree of input document
    org.w3c.dom.Document domDoc;
    public DOMtoJDOM(String systemID) throws Exception {
        DOMParser parser = new DOMParser();
        parser.parse(new InputSource(systemID));
        domDoc = parser.getDocument();
    }
    public org.jdom.Document convert()
        throws JDOMException, IOException
    {
        // Create new DOMBuilder, using default parser
        DOMBuilder builder = new DOMBuilder();
        org.jdom.Document jdomDoc = builder.build(domDoc);
        return jdomDoc;
    }
    public static void main(String[] args) {
        try {
            DOMtoJDOM tester = new DOMtoJDOM(args[0]);
            org.jdom.Document jdomDoc = tester.convert();
            // Output the document to System.out
            XMLOutputter outputter = new XMLOutputter();
            outputter.output(jdomDoc, System.out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
