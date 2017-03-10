package com.elovirta.dita.markdown;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

public class MarkdownReaderTest {

    private final DocumentBuilder db;

    public MarkdownReaderTest() {
        try {
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            db = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private void run(final String input) throws Exception {
        final Document act;
        try (final InputStream in = getClass().getResourceAsStream("/" + input)) {
            act = db.newDocument();
            final Transformer t = TransformerFactory.newInstance().newTransformer();
            final MarkdownReader r = new MarkdownReader();
            final InputSource i = new InputSource(in);
            t.transform(new SAXSource(r, i), new DOMResult(act));
        }

        final Document exp;
        try (final InputStream in = getClass().getResourceAsStream("/" + input.replaceAll("\\.md$", ".dita"));) {
            exp = db.parse(in);
        }

        assertXMLEqual(clean(exp), clean(act));
    }

    private Document clean(Document doc) {
        final NodeList elems = doc.getElementsByTagName("*");
        for (int i = 0; i < elems.getLength(); i++) {
            final Element elem = (Element) elems.item(i);
            elem.removeAttribute("domains");
            elem.removeAttributeNS("http://dita.oasis-open.org/architecture/2005/", "DITAArchVersion");
        }
        doc.normalizeDocument();
        return doc;
    }

    private void resetXMLUnit() {
        XMLUnit.setControlEntityResolver(null);
        XMLUnit.setTestEntityResolver(null);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
        XMLUnit.setNormalize(true);
        XMLUnit.setIgnoreComments(true);
    }

    @Before
    public void setUp() {
        resetXMLUnit();
    }

    @Test
    public void testComplex() throws Exception {
        run("test.md");
    }

    @Test
    public void testHeader() throws Exception {
        run("header.md");
    }

    @Test
    public void testPandocHeader() throws Exception {
        run("pandoc_header.md");
    }

    @Test(expected = ParseException.class)
    public void testInvalidHeader() throws Exception {
        run("invalid_header.md");
    }

    @Test(expected = ParseException.class)
    public void testInvalidSectionHeader() throws Exception {
        run("invalid_section_header.md");
    }

    @Test
    public void testHeaderAttributes() throws Exception {
        run("header_attributes.md");
    }

    @Test
    public void testConcept() throws Exception {
        run("concept.md");
    }

    @Test
    public void testTask() throws Exception {
        run("task.md");
    }

    @Test
    public void testTaskOneStep() throws Exception {
        run("taskOneStep.md");
    }

    @Test
    public void testReference() throws Exception {
        run("reference.md");
    }

    @Test
    public void testImage() throws Exception {
        run("image.md");
    }

    @Test
    public void testLink() throws Exception {
        run("link.md");
    }

    @Test
    public void testUl() throws Exception {
        run("ul.md");
    }

    @Test
    public void testOl() throws Exception {
        run("ol.md");
    }

    @Test
    public void testInline() throws Exception {
        run("inline.md");
    }

    @Test
    public void testTable() throws Exception {
        run("table.md");
    }

    @Test
    public void testQuote() throws Exception {
        run("quote.md");
    }

    @Test
    public void testEscape() throws Exception {
        run("escape.md");
    }

    @Test
    public void testDl() throws Exception {
        run("dl.md");
    }

    @Test
    public void testCodeblock() throws Exception {
        run("codeblock.md");
    }

    @Test
    public void testMultipleTopLevel() throws Exception {
        run("multiple_top_level.md");
    }
    
    @Test
    public void testMultipleTopLevelSpecialized() throws Exception {
        run("multiple_top_level_specialized.md");
    }
    
    @Test
    public void testNoBOM() throws Exception {
        run("testNoBOM.md");
    }

    @Test
    public void testBOM() throws Exception {
        run("testBOM.md");
    }
}