package io.github.erenriquezp.rora.imaging.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Comprueba que un XMP sea XML bien formado, sin DTD ni entidades externas. */
final class XmpValidator {

  private XmpValidator() {}

  static boolean isValid(byte[] xmp) {
    try {
      SAXParserFactory f = SAXParserFactory.newInstance();
      f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setNamespaceAware(true);
      f.newSAXParser().parse(new ByteArrayInputStream(xmp), new DefaultHandler());
      return true;
    } catch (SAXException | IOException | ParserConfigurationException | RuntimeException e) {
      return false;
    }
  }
}
