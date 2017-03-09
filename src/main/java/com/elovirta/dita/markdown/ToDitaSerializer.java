/**
 * Based on ToHtmlSerializer (C) 2010-2011 Mathias Doenitz
 */
package com.elovirta.dita.markdown;

import org.apache.commons.io.FilenameUtils;
import org.dita.dost.util.DitaClass;
import org.parboiled.common.StringUtils;
import org.pegdown.ast.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.xml.XMLConstants.NULL_NS_URI;
import static javax.xml.XMLConstants.XML_NS_URI;
import static org.dita.dost.util.Constants.*;
import static org.dita.dost.util.URLUtils.toURI;
import static org.dita.dost.util.XMLUtils.AttributesBuilder;
import static org.parboiled.common.Preconditions.checkArgNotNull;

public class ToDitaSerializer implements Visitor {

    private static final String COLUMN_NAME_COL = "col";

    private static final Attributes TOPIC_ATTS = new AttributesBuilder().add(ATTRIBUTE_NAME_CLASS, TOPIC_TOPIC.toString())
            .add(DITA_NAMESPACE, ATTRIBUTE_NAME_DITAARCHVERSION, ATTRIBUTE_PREFIX_DITAARCHVERSION + ":" + ATTRIBUTE_NAME_DITAARCHVERSION, "CDATA", "1.2")
            .add(ATTRIBUTE_NAME_DOMAINS, "(topic hi-d) (topic ut-d) (topic indexing-d) (topic hazard-d) (topic abbrev-d) (topic pr-d) (topic sw-d) (topic ui-d)")
            .build();
    private static final Attributes BODY_ATTS = buildAtts(TOPIC_BODY);
    private static final Attributes SECTION_ATTS = buildAtts(TOPIC_SECTION);
    private static final Attributes EXAMPLE_ATTS = buildAtts(TOPIC_EXAMPLE);
    private static final Attributes LI_ATTS = buildAtts(TOPIC_LI);
    private static final Attributes P_ATTS = buildAtts(TOPIC_P);
    private static final Attributes I_ATTS = buildAtts(HI_D_I);
    private static final Attributes B_ATTS = buildAtts(HI_D_B);
    private static final Attributes DD_ATTS = buildAtts(TOPIC_DD);
    private static final Attributes CODEPH_ATTS = buildAtts(PR_D_CODEPH);
    private static final Attributes CODEBLOCK_ATTS = buildAtts(PR_D_CODEBLOCK);
    private static final Attributes DT_ATTS = buildAtts(TOPIC_DT);
    private static final Attributes DEL_ATTS = new AttributesBuilder().add(ATTRIBUTE_NAME_CLASS, TOPIC_PH.toString()).add("importance", "deleted").build();
    private static final Attributes TITLE_ATTS = buildAtts(TOPIC_TITLE);
    private static final Attributes BLOCKQUOTE_ATTS = buildAtts(TOPIC_LQ);
    private static final Attributes UL_ATTS = buildAtts(TOPIC_UL);
    private static final Attributes DL_ATTS = buildAtts(TOPIC_DL);
    private static final Attributes DLENTRY_ATTS = buildAtts(TOPIC_DLENTRY);
    private static final Attributes OL_ATTS = buildAtts(TOPIC_OL);
    private static final Attributes TABLE_ATTS = buildAtts(TOPIC_TABLE);
    private static final Attributes TGROUP_ATTS = buildAtts(TOPIC_TGROUP);
    private static final Attributes COLSPEC_ATTS = buildAtts(TOPIC_COLSPEC);
    private static final Attributes TBODY_ATTS = buildAtts(TOPIC_TBODY);
    private static final Attributes THEAD_ATTS = buildAtts(TOPIC_THEAD);
    private static final Attributes TR_ATTS = buildAtts(TOPIC_ROW);
    private static final Attributes IMAGE_ATTS = buildAtts(TOPIC_IMAGE);
    private static final Attributes XREF_ATTS = buildAtts(TOPIC_XREF);
    private static final Attributes ALT_ATTS = buildAtts(TOPIC_ALT);
    private static final Attributes PH_ATTS = buildAtts(TOPIC_PH);
    private static final Attributes ENTRY_ATTS = buildAtts(TOPIC_ENTRY);
    private static final Attributes FIG_ATTS = buildAtts(TOPIC_FIG);
    private static final Attributes EMPTY_ATTS = new AttributesImpl();

    private static final Map<String, DitaClass> sections = new HashMap<>();
    static {
        sections.put(TOPIC_SECTION.localName, TOPIC_SECTION);
        sections.put(TOPIC_EXAMPLE.localName, TOPIC_EXAMPLE);
    }

    private final ContentHandler contentHandler;
    private final Map<String, ReferenceNode> references = new HashMap<>();
    private final Map<String, String> abbreviations = new HashMap<>();
    private TableNode currentTableNode;
    private int currentTableColumn;

    private final Deque<DitaClass> tagStack = new ArrayDeque<>();
    private boolean inSection = false;

    /**
     * Current header level.
     */
    private int headerLevel = 0;

    public ToDitaSerializer(final ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    private static Attributes buildAtts(final DitaClass cls) {
        return new AttributesBuilder()
                .add(ATTRIBUTE_NAME_CLASS, cls.toString())
                .build();
    }

    public void toHtml(final RootNode astRoot) throws SAXException {
        checkArgNotNull(astRoot, "astRoot");
        clean(astRoot);
        final boolean isCompound = hasMultipleTopLevelHeaders(astRoot);
        contentHandler.startDocument();
        contentHandler.startPrefixMapping(ATTRIBUTE_PREFIX_DITAARCHVERSION, DITA_NAMESPACE);
        if (isCompound) {
            contentHandler.startElement(NULL_NS_URI, ELEMENT_NAME_DITA, ELEMENT_NAME_DITA, EMPTY_ATTS);
        }
        try {
            astRoot.accept(this);
            while (!tagStack.isEmpty()) {
                endElement();
            }
        } catch (final ParseException e) {
            //e.printStackTrace();
            throw new SAXException("Failed to parse Markdown: " + e.getMessage(), e);
        }
        if (isCompound) {
            contentHandler.endElement(NULL_NS_URI, ELEMENT_NAME_DITA, ELEMENT_NAME_DITA);
        }
        contentHandler.endPrefixMapping(ATTRIBUTE_PREFIX_DITAARCHVERSION);
        contentHandler.endDocument();
    }

    private boolean hasMultipleTopLevelHeaders(RootNode astRoot) {
        final long count = astRoot.getChildren().stream()
                .filter(n -> (n instanceof HeaderNode) && (((HeaderNode) n).getLevel() == 1))
                .count();
        return count > 1;
    }

    /**
     * Replace metadata para with actual metadata element. Modifies AST <b>in-place</b>.
     */
    void clean(final RootNode node) {
        final Map<String, String> metadata = new HashMap<>();

        // read pandoc_title_block
        final Node first = node.getChildren().get(0);
        if (first instanceof ParaNode && toString(first).startsWith("%")) {
            final String[] fields = toString(first).split("\n");
            if (fields.length >= 1) {
                metadata.put("title", fields[0].substring(1));
            }
//            if (fields.length >= 2) {
//                metadata.put("authors", fields[0].substring(1));
//            }
//            if (fields.length >= 3) {
//                metadata.put("date", fields[0].substring(1));
//            }
        }

        // insert header
        if (metadata.containsKey("title")) {
            boolean levelOneFound = false;
            for (final Node c: node.getChildren()) {
                if (c instanceof HeaderNode) {
                    final HeaderNode h = (HeaderNode) c;
                    if (h.getLevel() == 1) {
                        levelOneFound = true;
                        break;
                    }
                }
            }
            final Node m;
            if (levelOneFound) {
                m = new MetadataNode(metadata.get("title"));
            } else {
                m = new HeaderNode(1, new TextNode(metadata.get("title")));
            }
            node.getChildren(). set(0, m);
        }
    }

    // Visitor methods

    private static void toString(final Node node, final StringBuilder buf) {
        if (node instanceof SuperNode) {
            for (final Node n : node.getChildren()) {
                toString(n, buf);
            }
        } else if (node instanceof TextNode) {
            buf.append(((TextNode) node).getText());
            for (final Node n : node.getChildren()) {
                toString(n, buf);
            }
        } else if (node instanceof SimpleNode) {
            buf.append(toString((SimpleNode) node));
        } else {
            throw new ParseException("Trying to process " + node);
        }
    }

    private static String toString(final Node node) {
        final StringBuilder buf = new StringBuilder();
        toString(node, buf);
        return buf.toString();
    }

    @Override
    public void visit(final RootNode node) {
        for (final ReferenceNode refNode : node.getReferences()) {
            references.put(normalize(toString(refNode)), refNode);
        }
//        for (final AbbreviationNode abbrNode : node.getAbbreviations()) {
//            visitChildren(abbrNode);
//            String abbr = printer.getString();
//            printer.clear();
//            abbrNode.getExpansion().accept(this);
//            String expansion = printer.getString();
//            abbreviations.put(abbr, expansion);
//            printer.clear();
//        }
        visitChildren(node);
    }

    @Override
    public void visit(final AbbreviationNode node) {
    }

    @Override
    public void visit(AnchorLinkNode node) {
        throw new UnsupportedOperationException(node.toString());
    }

    @Override
    public void visit(final AutoLinkNode node) {
        final AttributesBuilder atts = getLinkAttributes(node.getText());

        startElement(TOPIC_XREF, atts.build());
        visitChildren(node);
        endElement();
    }

    @Override
    public void visit(final BlockQuoteNode node) {
        printTag(node, TOPIC_LQ, BLOCKQUOTE_ATTS);
    }

    @Override
    public void visit(final BulletListNode node) {
        printTag(node, TOPIC_UL, UL_ATTS);
    }

    @Override
    public void visit(final CodeNode node) {
        printTag(node, PR_D_CODEPH, CODEPH_ATTS);
    }

    @Override
    public void visit(final DefinitionListNode node) {
        startElement(TOPIC_DL, DL_ATTS);
        DitaClass previous = null;
        for (final Node child : node.getChildren()) {
            if (previous == null) {
                startElement(TOPIC_DLENTRY, DLENTRY_ATTS);
            }
            if (child instanceof DefinitionTermNode) {
                if (TOPIC_DD.equals(previous)) {
                    endElement(); // dlentry
                    startElement(TOPIC_DLENTRY, DLENTRY_ATTS);
                }
            }
            child.accept(this);
            previous = (child instanceof DefinitionTermNode) ? TOPIC_DT : TOPIC_DD;
        }
        endElement(); // dlentry
        endElement(); // dl
    }

    @Override
    public void visit(final DefinitionNode node) {
        printTag(node, TOPIC_DD, DD_ATTS);
    }

    @Override
    public void visit(final DefinitionTermNode node) {
        if (tagStack.peek().equals(TOPIC_DL)) {
            startElement(TOPIC_DLENTRY, DLENTRY_ATTS);
        }
        printTag(node, TOPIC_DT, DT_ATTS);
    }

    @Override
    public void visit(final ExpImageNode node) {
        writeImage(node, node.title, null, node.url, null);
    }

    private void writeImage(SuperNode node, final String title, final String alt, final String url, final String key) {
        final AttributesBuilder atts = new AttributesBuilder(IMAGE_ATTS)
                .add(ATTRIBUTE_NAME_HREF, url);
        if (key != null) {
            atts.add(ATTRIBUTE_NAME_KEYREF, key);
        }

        if (!title.isEmpty()) {
            startElement(TOPIC_FIG, FIG_ATTS);
            startElement(TOPIC_TITLE, TITLE_ATTS);
            characters(title);
            endElement();
            startElement(TOPIC_IMAGE, atts.build());
            if (hasChildren(node)) {
                startElement(TOPIC_ALT, ALT_ATTS);
                if (alt != null) {
                    characters(alt);
                } else {
                    visitChildren(node);
                }
                endElement();
            }
            endElement();
            endElement();
        } else if (onlyImageChild) {
            atts.add("placement", "break");
            startElement(TOPIC_IMAGE, atts.build());
            if (hasChildren(node)) {
                startElement(TOPIC_ALT, ALT_ATTS);
                if (alt != null) {
                    characters(alt);
                } else {
                    visitChildren(node);
                }
                endElement();
            }
            endElement();
        } else {
            startElement(TOPIC_IMAGE, atts.build());
            if (hasChildren(node)) {
                startElement(TOPIC_ALT, ALT_ATTS);
                if (alt != null) {
                    characters(alt);
                } else {
                    visitChildren(node);
                }
                endElement();
            }
            endElement();
        }
    }

    @Override
    public void visit(final ExpLinkNode node) {
        final AttributesBuilder atts = getLinkAttributes(node.url);
        //atts.add("title", node.title);

        startElement(TOPIC_XREF, atts.build());
        visitChildren(node);
        endElement();
    }

    private static <E> E containsSome(final Collection<E> col, final Collection<E> find) {
        for (final E c : col) {
            if (find.contains(c)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public void visit(final HeaderNode node) {
        if (node.getLevel() > headerLevel + 1) {
            throw new ParseException("Header level raised from " + headerLevel + " to " + node.getLevel() + " without intermediate header level");
        }
        final Title header = new Title(node);

        if (inSection) {
            endElement(); // section or example
            inSection = false;
        }
        final String section = containsSome(header.classes, sections.keySet());
        if (section != null) {
            if (node.getLevel() <= headerLevel) {
                throw new ParseException("Level " + node.getLevel() + " section title must be higher level than parent topic title " + headerLevel);
            }
            final DitaClass cls = sections.get(section);
            final AttributesBuilder atts = new AttributesBuilder()
                    .add(ATTRIBUTE_NAME_CLASS, cls.toString());
            if (header.id != null) {
                atts.add(ATTRIBUTE_NAME_ID, header.id);
            } else {
                atts.add(ATTRIBUTE_NAME_ID, getId(header.title));
            }
            final Collection<String> classes = new ArrayList<>(header.classes);
            classes.removeAll(sections.keySet());
            if (!classes.isEmpty()) {
                atts.add("outputclass", StringUtils.join(classes, " "));
            }
            startElement(cls, atts.build());
            inSection = true;
            startElement(TOPIC_TITLE, TITLE_ATTS);
            characters(header.title);
            endElement(); // title
        } else {
            if (headerLevel > 0) {
                endElement(); // body
            }
            for (; node.getLevel() <= headerLevel; headerLevel--) {
                endElement(); // topic
            }
            headerLevel = node.getLevel();

            final AttributesBuilder atts = new AttributesBuilder(TOPIC_ATTS);
            if (header.id != null) {
                atts.add(ATTRIBUTE_NAME_ID, header.id);
            } else {
                atts.add(ATTRIBUTE_NAME_ID, getId(header.title));
            }
            if (!header.classes.isEmpty()) {
                atts.add("outputclass", StringUtils.join(header.classes, " "));
            }
            startElement(TOPIC_TOPIC, atts.build());
            startElement(TOPIC_TITLE, TITLE_ATTS);
            characters(header.title);
            endElement(); // title
            startElement(TOPIC_BODY, BODY_ATTS);
        }
    }

    static class Metadata {
        final String id;
        final List<String> classes;

        Metadata(final String id, final List<String> classes) {
            this.id = id;
            this.classes = classes;
        }

        static Metadata parse(final String contents) {
            final String c = contents.trim();
            final List<String> classes = new ArrayList<>();
            String fragment = null;
            for (final String t : c.split("\\s+")) {
                if (t.startsWith("#")) {
                    fragment = t.substring(1);
                } else if (t.startsWith(".")) {
                    classes.add(t.substring(1));
                }
            }
            final String id = fragment != null ? fragment : null;
            return new Metadata(id, classes);
        }
    }

    static class Title {
        final String title;
        final String id;
        final Collection<String> classes;

        Title(final HeaderNode node) {
            final String contents = ToDitaSerializer.toString(node);
            classes = new ArrayList<>();
            final Pattern p = Pattern.compile("^(.+?)(?:\\s*#{" + node.getLevel() + "}?\\s*)?(?:\\{(.+?)\\})?$");
            final Matcher m = p.matcher(contents);
            if (m.matches()) {
                title = m.group(1);
                if (m.group(2) != null) {
                    final Metadata metadata = Metadata.parse(m.group(2));
                    classes.addAll(metadata.classes);
                    id = metadata.id; // != null ? metadata.id : getId(title);
                } else {
//                    id = getId(title);
                    id = null;
                }
            } else {
                title = contents;
//                id = getId(contents);
                id = null;
            }
        }
    }

//    private String getId(final HeaderNode node) {
//        return getId(toString(node));
//    }

    private static String getId(final String contents) {
        return contents.toLowerCase().replaceAll("[^\\w]", " ").trim().replaceAll("\\s+", "_");
    }

    @Override
    public void visit(final HtmlBlockNode node) {
        final String text = node.getText();
        characters(text);
    }

    @Override
    public void visit(final InlineHtmlNode node) {
        characters(node.getText());
    }

    @Override
    public void visit(final ListItemNode node) {
        printTag(node, TOPIC_LI, LI_ATTS);
    }

    @Override
    public void visit(final MailLinkNode node) {
        final AttributesBuilder atts = getLinkAttributes("mailto:" + node.getText());

        startElement(TOPIC_XREF, atts.build());
        visitChildren(node);
        endElement();
    }

    @Override
    public void visit(final OrderedListNode node) {
        printTag(node, TOPIC_OL, OL_ATTS);
    }

    private boolean onlyImageChild = false;

    @Override
    public void visit(final ParaNode node) {
        if (containsImage(node)) {
            onlyImageChild = true;
            visitChildren(node);
            onlyImageChild = false;
        } else {
            printTag(node, TOPIC_P, P_ATTS);
        }
    }

    /**
     * Contains only single image
     */
    private boolean containsImage(final SuperNode node) {
        if (node.getChildren().size() != 1) {
            return false;
        } else {
            final Node first = node.getChildren().get(0);
            if (first instanceof ExpImageNode || first instanceof RefImageNode) {
                return true;
            } else if (first instanceof SuperNode) {
                return containsImage((SuperNode) first);
            }
        }
        return false;
    }

    @Override
    public void visit(final QuotedNode node) {
        switch (node.getType()) {
            case DoubleAngle:
                characters('\u00AB');//&laquo;
                visitChildren(node);
                characters('\u00AB');//&laquo;
                break;
            case Double:
                characters('\u201C');//"&ldquo;"
                visitChildren(node);
                characters('\u201C');//"&ldquo;"
                break;
            case Single:
                characters('\u2018');//"&lsquo;"
                visitChildren(node);
                characters('\u2018');//"&lsquo;"
                break;
        }
    }

    @Override
    public void visit(final ReferenceNode node) {
        // reference nodes are not printed
    }

    @Override
    public void visit(final RefImageNode node) {
        final String text = toString(node);
        final String key = node.referenceKey != null ? toString(node.referenceKey) : text;
        final ReferenceNode refNode = references.get(normalize(key));
        if (refNode == null) { // "fake" reference image link
            final Attributes atts = new AttributesBuilder(IMAGE_ATTS)
                    .add(ATTRIBUTE_NAME_KEYREF, key)
                    .build();
            startElement(TOPIC_IMAGE, atts);
            if (node.referenceKey != null) {
                visitChildren(node);
            }
            endElement();
        } else {
            writeImage(refNode, refNode.getTitle(), text, refNode.getUrl(), key);
        }
    }

    @Override
    public void visit(final RefLinkNode node) {
        final String text = toString(node);
        final String key = node.referenceKey != null ? toString(node.referenceKey) : text;
        final ReferenceNode refNode = references.get(normalize(key));
        if (refNode == null) { // "fake" reference link
            final AttributesBuilder atts = new AttributesBuilder(XREF_ATTS)
                    .add(ATTRIBUTE_NAME_KEYREF, key);
            startElement(TOPIC_XREF, atts.build());
            if (node.referenceKey != null) {
                visitChildren(node);
            }
            endElement();
        } else {
            final AttributesBuilder atts = getLinkAttributes(refNode.getUrl());
            startElement(TOPIC_XREF, atts.build());
            if (refNode.getTitle() != null) {
                characters(refNode.getTitle());
            } else {
                visitChildren(node);
            }
            endElement();
        }
    }

    @Override
    public void visit(final SimpleNode node) {
        switch (node.getType()) {
            case Apostrophe:
                characters('\u2019');//"&rsquo;"
                break;
            case Ellipsis:
                characters('\u2026');//"&hellip;"
                break;
            case Emdash:
                characters('\u2014');//"&mdash;"
                break;
            case Endash:
                characters('\u2013');//"&ndash;"
                break;
            case HRule:
//                startElement(TOPIC_PH, new AttributesBuilder().add(ATTRIBUTE_NAME_CLASS, "+ html-d/hr ").build());
//                endElement();
                characters('\n');
                break;
            case Linebreak:
//                startElement(TOPIC_PH, new AttributesBuilder().add(ATTRIBUTE_NAME_CLASS, "+ html-d/br ").build());
//                endElement();
                characters('\n');
                break;
            case Nbsp:
                characters("\u00A0");//&nbsp;
                break;
            default:
                throw new ParseException("Unsupported simple node: " + node.getType());
        }
    }

    private static String toString(final SimpleNode node) {
        switch (node.getType()) {
            case Apostrophe:
                return "\u2019";//"&rsquo;"
            case Ellipsis:
                return "\u2026";//"&hellip;"
            case Emdash:
                return "\u2014'";//"&mdash;"
            case Endash:
                return "\u2013";//"&ndash;"
            case Linebreak:
            case HRule:
                return "\n";
            case Nbsp:
                return "\u00A0";//&nbsp;
            default:
                throw new ParseException("Unsupported simple node: " + node.getType());
        }
    }


    @Override
    public void visit(final StrongEmphSuperNode node) {
        if (node.isClosed()) {
            if (node.isStrong()) {
                printTag(node, HI_D_B, B_ATTS);
            } else {
                printTag(node, HI_D_I, I_ATTS);
            }
        } else {
            //sequence was not closed, treat open chars as ordinary chars
            characters(node.getChars());
            visitChildren(node);
        }
    }

    @Override
    public void visit(final StrikeNode node) {
        printTag(node, TOPIC_PH, DEL_ATTS);
    }

    @Override
    public void visit(final TableBodyNode node) {
        printTag(node, TOPIC_TBODY, TBODY_ATTS);
    }

    @Override
    public void visit(final TableCaptionNode node) {
        startElement(TOPIC_TITLE, TITLE_ATTS);
        visitChildren(node);
        endElement();
    }

    @Override
    public void visit(final TableCellNode node) {
        final List<TableColumnNode> columns = currentTableNode.getColumns();
        final TableColumnNode column = columns.get(Math.min(currentTableColumn, columns.size() - 1));

        final AttributesBuilder atts = new AttributesBuilder(ENTRY_ATTS);
        column.accept(this);
        if (tableColumnAlignment != null) {
            atts.add(ATTRIBUTE_NAME_ALIGN, tableColumnAlignment);
            tableColumnAlignment = null;
        }
        if (node.getColSpan() > 1) {
            atts.add(ATTRIBUTE_NAME_NAMEST, COLUMN_NAME_COL + Integer.toString(currentTableColumn + 1));
            atts.add(ATTRIBUTE_NAME_NAMEEND, COLUMN_NAME_COL + Integer.toString(currentTableColumn + node.getColSpan()));
        }
        startElement(TOPIC_ENTRY, atts.build());
        visitChildren(node);
        endElement();

        currentTableColumn += node.getColSpan();
    }

    private String tableColumnAlignment = null;

    @Override
    public void visit(final TableColumnNode node) {
        switch (node.getAlignment()) {
            case None:
                break;
            case Left:
                tableColumnAlignment = "left";
                break;
            case Right:
                tableColumnAlignment = "right";
                break;
            case Center:
                tableColumnAlignment = "center";
                break;
            default:
                throw new ParseException("Unsupported table column alignment: " + node.getAlignment());
        }
    }

    @Override
    public void visit(final TableHeaderNode node) {
        printTag(node, TOPIC_THEAD, THEAD_ATTS);
    }

    @Override
    public void visit(final TableNode node) {
        currentTableNode = node;
        startElement(TOPIC_TABLE, TABLE_ATTS);
        for (final Node child : node.getChildren()) {
            if (child instanceof TableCaptionNode) {
                child.accept(this);
            }
        }
        final Attributes atts = new AttributesBuilder(TGROUP_ATTS)
                .add(ATTRIBUTE_NAME_COLS, Integer.toString(node.getColumns().size()))
                .build();
        startElement(TOPIC_TGROUP, atts);

        int counter = 1;
        for (final TableColumnNode col : node.getColumns()) {
            final AttributesBuilder catts = new AttributesBuilder(COLSPEC_ATTS)
                    .add(ATTRIBUTE_NAME_COLNAME, COLUMN_NAME_COL + counter);
            switch (col.getAlignment()) {
                case Center:
                    catts.add(ATTRIBUTE_NAME_ALIGN, "center");
                    break;
                case Right:
                    catts.add(ATTRIBUTE_NAME_ALIGN, "right");
                    break;
                case Left:
                    catts.add(ATTRIBUTE_NAME_ALIGN, "left");
                    break;
            }
            startElement(TOPIC_COLSPEC, catts.build());
            endElement(); // colspec
            counter++;
        }
        for (final Node child : node.getChildren()) {
            if (!(child instanceof TableCaptionNode)) {
                child.accept(this);
            }
        }
        endElement(); // tgroup
        endElement(); // table
        currentTableNode = null;
    }

    @Override
    public void visit(final TableRowNode node) {
        currentTableColumn = 0;
        printTag(node, TOPIC_ROW, TR_ATTS);
    }

    @Override
    public void visit(final VerbatimNode node) {
        final AttributesBuilder atts = new AttributesBuilder(CODEBLOCK_ATTS)
            .add(XML_NS_URI, "space", "xml:space", "CDATA", "preserve");
        if (!StringUtils.isEmpty(node.getType())) {
            final String type = node.getType().trim();
            final Metadata metadata;
            if (type.startsWith("{")) {
                metadata = Metadata.parse(type.substring(1, type.length() - 1));
            } else {
                metadata = new Metadata(null, Arrays.asList(type));
            }
            if (metadata.id != null) {
                atts.add(ATTRIBUTE_NAME_ID, metadata.id);
            }
            if (!metadata.classes.isEmpty()) {
                atts.add("outputclass", StringUtils.join(metadata.classes, " "));
            }
        }
        startElement(PR_D_CODEBLOCK, atts.build());
        String text = node.getText();
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        characters(text);
        endElement();
    }

    @Override
    public void visit(final WikiLinkNode node) {
        String url;
        try {
            url = "./" + URLEncoder.encode(node.getText().replace(' ', '-'), "UTF-8") + ".html";
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        final AttributesBuilder atts = getLinkAttributes(url);

        startElement(TOPIC_XREF, atts.build());
        //characters(rendering.text);
        visitChildren(node);
        endElement();
    }

    @Override
    public void visit(final TextNode node) {
        if (abbreviations.isEmpty()) {
            characters(node.getText());
        } else {
            printWithAbbreviations(node.getText());
        }
    }

    @Override
    public void visit(final SpecialTextNode node) {
        characters(node.getText());
    }

    @Override
    public void visit(final SuperNode node) {
        visitChildren(node);
    }

    @Override
    public void visit(final Node node) {
        if (node instanceof MetadataNode) {
            final MetadataNode n = (MetadataNode) node;
            final String id = getId(n.title);
            startElement(TOPIC_TOPIC, new AttributesBuilder(TOPIC_ATTS).add(ATTRIBUTE_NAME_ID, id).build());
            startElement(TOPIC_TITLE, TITLE_ATTS);
            characters(n.title);
            endElement();
        } else {
            visitChildren(node);
        }
//        for (final ToDitaSerializerPlugin plugin : plugins) {
//            if (plugin.visit(node, this, printer)) {
//                return;
//            }
//        }
    }

    // helpers

    private boolean hasChildren(final Node node) {
        if (node instanceof SuperNode) {
            return !node.getChildren().isEmpty();
        } else if (node instanceof TextNode) {
            return !node.getChildren().isEmpty();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void visitChildren(final Node node) {
        for (final Node child : node.getChildren()) {
            child.accept(this);
        }
    }

    private void printTag(TextNode node, final DitaClass tag, final Attributes atts) {
        startElement(tag, atts);
        characters(node.getText());
        endElement();
    }

    private void printTag(SuperNode node, final DitaClass tag, final Attributes atts) {
        startElement(tag, atts);
        visitChildren(node);
        endElement();
    }

    private AttributesBuilder getLinkAttributes(final String href) {
        final AttributesBuilder atts = new AttributesBuilder(XREF_ATTS)
                .add(ATTRIBUTE_NAME_HREF, href);

        final String ext = FilenameUtils.getExtension(href).toLowerCase();
        String format;
        switch (ext) {
            case ATTR_FORMAT_VALUE_DITA:
            case "xml":
                // Markdown is converted to DITA
            case "md":
            case "markdown":
                format = "markdown";
                break;
            default:
                format = !ext.isEmpty() ? ext : "html";
                break;
        }
        if (format != null) {
            atts.add(ATTRIBUTE_NAME_FORMAT, format);
        }

        final URI uri = toURI(href);
        if (uri != null && uri.isAbsolute()) {
            atts.add(ATTRIBUTE_NAME_SCOPE, ATTR_SCOPE_VALUE_EXTERNAL);
        }

        return atts;
    }

    private String normalize(final String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case ' ':
                case '\n':
                case '\t':
                    continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private void printWithAbbreviations(String string) {
        Map<Integer, Map.Entry<String, String>> expansions = null;

        for (Map.Entry<String, String> entry : abbreviations.entrySet()) {
            // first check, whether we have a legal match
            String abbr = entry.getKey();

            int ix = 0;
            while (true) {
                final int sx = string.indexOf(abbr, ix);
                if (sx == -1) break;

                // only allow whole word matches
                ix = sx + abbr.length();

                if (sx > 0 && Character.isLetterOrDigit(string.charAt(sx - 1))) continue;
                if (ix < string.length() && Character.isLetterOrDigit(string.charAt(ix))) {
                    continue;
                }

                // ok, legal match so save an expansions "task" for all matches
                if (expansions == null) {
                    expansions = new TreeMap<>();
                }
                expansions.put(sx, entry);
            }
        }

        if (expansions != null) {
            int ix = 0;
            for (Map.Entry<Integer, Map.Entry<String, String>> entry : expansions.entrySet()) {
                int sx = entry.getKey();
                final String abbr = entry.getValue().getKey();
                final String expansion = entry.getValue().getValue();

                characters(string.substring(ix, sx));
                final AttributesBuilder atts = new AttributesBuilder(PH_ATTS);
                if (StringUtils.isNotEmpty(expansion)) {
                    atts.add(ATTRIBUTE_NAME_OTHERPROPS, expansion);
                }
                startElement(TOPIC_PH, atts.build());
                characters(abbr);
                endElement();
                ix = sx + abbr.length();
            }
            characters(string.substring(ix));
        } else {
            characters(string);
        }
    }

    // ContentHandler methods

    private void startElement(final DitaClass tag, final Attributes atts) {
        try {
            contentHandler.startElement(NULL_NS_URI, tag.localName, tag.localName, atts);
        } catch (final SAXException e) {
            throw new ParseException(e);
        }
        tagStack.addFirst(tag);
    }

    private void endElement() {
        if (!tagStack.isEmpty()) {
            endElement(tagStack.removeFirst());
        }
    }


    private void endElement(final DitaClass tag) {
        try {
            contentHandler.endElement(NULL_NS_URI, tag.localName, tag.localName);
        } catch (final SAXException e) {
            throw new ParseException(e);
        }
    }

    private void characters(final char c) {
        try {
            contentHandler.characters(new char[]{c}, 0, 1);
        } catch (final SAXException e) {
            throw new ParseException(e);
        }
    }

    private void characters(final String t) {
        final char[] cs = t.toCharArray();
        try {
            contentHandler.characters(cs, 0, cs.length);
        } catch (final SAXException e) {
            throw new ParseException(e);
        }
    }

    private class MetadataNode extends AbstractNode {
        final String title;

        public MetadataNode(final String title) {
            this.title = title;
        }

        @Override
        public void accept(final Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public List<Node> getChildren() {
            return Collections.emptyList();
        }
    }
}
