package com.elovirta.dita.markdown;

import org.pegdown.Extensions;
import org.pegdown.LinkRenderer;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;
import org.xml.sax.*;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import java.io.BufferedInputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;

/**
 * XMLReader implementation for Markdown.
 */
public class MarkdownReader implements XMLReader {

    private final PegDownProcessor p;
    private final SAXTransformerFactory tf;
    private final Templates t;

    private EntityResolver resolver;
    private ContentHandler contentHandler;
    private ErrorHandler errorHandler;

    public MarkdownReader() {
        p = new PegDownProcessor(Extensions.ALL - Extensions.SMARTYPANTS);
        try {
            final URI style = getClass().getResource("/specialize.xsl").toURI();
            tf = (SAXTransformerFactory) TransformerFactory.newInstance();
            t = tf.newTemplates(new StreamSource(style.toString()));
        } catch (final URISyntaxException | TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    @Override
    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        // NOOP
    }

    @Override
    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return null;
    }

    @Override
    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        // NOOP
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public EntityResolver getEntityResolver() {
        return resolver;
    }

    @Override
    public void setDTDHandler(final DTDHandler handler) {
        // NOOP
    }

    @Override
    public DTDHandler getDTDHandler() {
        return null;
    }

    @Override
    public void setContentHandler(final ContentHandler handler) {
        this.contentHandler = handler;
    }

    @Override
    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    @Override
    public void setErrorHandler(final ErrorHandler handler) {
        this.errorHandler = handler;
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        final char[] markdownContent = getMarkdownContent(input);
        final RootNode root = p.parseMarkdown(markdownContent);
        parseAST(root);
    }

    @Override
    public void parse(final String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    private char[] getMarkdownContent(final InputSource input) throws IOException {
        final CharArrayWriter out = new CharArrayWriter();
        if (input.getByteStream() != null) {
            final String encoding = input.getEncoding() != null ? input.getEncoding() : "UTF-8";
            final InputStream is = 
            		"UTF-8".equals(encoding)?
            			consumeBOM(input.getByteStream()):
            			input.getByteStream();
            final Reader in = new InputStreamReader(is, encoding);
            try {
                copy(in, out);
            } finally {
                closeQuietly(in);
                //closeQuietly(out);
            }
        } else if (input.getCharacterStream() != null) {
            final Reader in = input.getCharacterStream();
            try {
                copy(in, out);
            } finally {
                closeQuietly(in);
                //closeQuietly(out);
            }
        } else if (input.getSystemId() != null) {
            final URL inUrl;
            try {
                inUrl = new URI(input.getSystemId()).toURL();
            } catch (final URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            final String encoding = input.getEncoding() != null ? input.getEncoding() : "UTF-8";
            final Reader in = new InputStreamReader(inUrl.openStream(), encoding);
            try {
                copy(in, out);
            } finally {
                closeQuietly(in);
                //closeQuietly(out);
            }
        }
        return out.toCharArray();
    }

    /**
     * Returns an input stream that skips the BOM if present.
     * 
     * @param byteStream the original input stream
     * @return An input stream without a possible BOM
     * @throws IOException
     */
    private InputStream consumeBOM(InputStream in) throws IOException {
		BufferedInputStream bin = new BufferedInputStream(in);
		bin.mark(3);
		try {
		    int b0 = (byte)bin.read() & 0xFF;
		    int b1 = (byte)bin.read() & 0xFF;
		    int b2 = (byte)bin.read() & 0xFF;
		    
		    if (b0 != 0xEF || b1 != 0xBB || b2 != 0xBF) {
		    	bin.reset();
		    } // else we have the UTF-8 BOM and we just continue
		} catch (IOException e) {
			bin.reset();
		}
		return bin;
	}

	private void parseAST(final RootNode root) throws SAXException {
        final TransformerHandler h;
        try {
            h = tf.newTransformerHandler(t);
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e);
        }
        h.setResult(new SAXResult(contentHandler));
        final ToDitaSerializer s = new ToDitaSerializer(h);
        s.toHtml(root);
    }

}
