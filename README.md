**Deprecation Note**

This repository has been deprecated and all further development has been moved to https://github.com/jelovirt/com.elovirta.lwdita.

---

Lightweight DITA for DITA-OT
============================

DITA-OT LwDITA plug-in contains

-   a custom parser for Markdown and HTML to allow using
    MDITA and HDITA as a source document format,
-   and a transtype to generate Markdown from DITA source.

The Markdown DITA files need to use a subset of Markdown constructs for
compatibility with DITA content models.


Usage
-----

### Using LwDITA files as input

Markdown DITA or HTML DITA topics can only be used by linking to them in map files.

~~~~ {.xml}
<map>
  <topicref href="test.md" format="mdita"/>
  <topicref href="test.html" format="hdita"/>
</map>
~~~~

The `format` attribute value must be set to `mdita` or `hdita` in order to
recognize files as Markdown DITA or HTML DITA, respectively; the file extension is not used to
recognize format.

See [Syntax reference](https://github.com/jelovirt/dita-ot-markdown/wiki/Syntax-reference) for XML and Markdown DITA correspondence.

### Generating Markdown output

The DITA-OT LwDITA plug-in extends the DITA Open Toolkit with additional output formats _(transformation types)_ that can be used to publish DITA content as Markdown.

* To publish Markdown DITA files, use the `markdown` transtype.

* To generate [GitHub Flavored Markdown](https://help.github.com/categories/writing-on-github/) files, use the `markdown_github` transtype.

* To publish GitHub Flavored Markdown and generate a  `SUMMARY.md` table of contents file for publication via [GitBook](https://www.gitbook.com), use the `markdown_gitbook` transtype.

Requirements
------------

DITA-OT 2.2 is required. Earlier versions of DITA-OT do not have the
required extension points.

Install
-------

1.  Run the plug-in installation command:

    ~~~~ {.sh}
    dita -install https://github.com/jelovirt/dita-ot-markdown/releases/download/1.3.0/com.elovirta.dita.markdown_1.3.0.zip
    ~~~~

The `dita` command line tool requires no additional configuration;
running DITA-OT using Ant requires adding plug-in contributed JAR files
to `CLASSPATH` with e.g. `-lib plugins/com.elovirta.dita.markdown`.

Build
-----

To build the DITA-OT Markdown plug-in from source:

1.  Install the DITA-OT distribution JAR file dependencies by running `gradle install` from your clone of the [DITA-OT repository](https://github.com/dita-ot/dita-ot).
    The required dependencies are installed to a local Maven repository in your home directory under `.m2/repository/org/dita-ot/dost/`. 
2.  Run the Gradle distribution task to generate the plug-in distribution package:

    ~~~~ {.sh}
    ./gradlew dist
    ~~~~

    The distribution ZIP file is generated under `build/distributions`.

Donating
--------

Support this project and others by [@jelovirt](https://github.com/jelovirt) via [Paypal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=jarno%40elovirta%2ecom&lc=FI&item_name=Support%20Open%20Source%20work&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donate_LG%2egif%3aNonHosted).

License
-------

DITA-OT LwDITA is licensed for use under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
