package org.kritikal.fabric.annotations;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.kritikal.fabric.core.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

public class CFNoscriptRenderers {

    final static Logger logger = LoggerFactory.getLogger(CFNoscriptRenderers.class);

    static {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
    }
    static final TransformerFactory tfactory = TransformerFactory.newInstance();

    public void add(Pattern pattern, Function<CFXmlParameters, String> processor)
    {
        array.add(new CFXmlRenderer(pattern, processor));
    }

    private ConcurrentHashMap<String, Transformer> map = new ConcurrentHashMap<>();
    public Transformer get(String filesystemLocation) {
        return map.computeIfAbsent(filesystemLocation, (file)->{
            try {
                final Reader xslReader = new BufferedReader(new FileReader(file));
                try {
                    StreamSource xslSource = new StreamSource(xslReader, file);
                    return tfactory.newTransformer(xslSource);
                } catch (TransformerConfigurationException e) {
                    logger.warn(e);
                    return null;
                } finally {
                    try {
                        xslReader.close();
                    } catch (IOException e) {
                    }
                }
            }
            catch (FileNotFoundException e) {
                logger.warn(e);
                return null;
            }
        });
    }

    public static String nodeToString(Node node) {
        StringWriter buffer = new StringWriter();
        try {
            Transformer transformer = tfactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(node),
                    new StreamResult(buffer));
            String str = buffer.toString();
            int i = str.indexOf("<body>");
            if (i>=0) str = str.substring(i+6);
            int j = str.indexOf("</body>");
            if (j >= 0) {
                str = str.substring(0,j);
            }
            return str;
        }
        catch (TransformerException e) {
            return null;
        }
        finally {
            try { buffer.close(); } catch (IOException e) { }
        }
    }

    public static class CFXmlRenderer {
        public CFXmlRenderer(Pattern pattern, Function<CFXmlParameters, String> processor) {
            this.pattern = pattern;
            this.processor = processor;
        }
        public final Pattern pattern;
        public final Function<CFXmlParameters, String> processor;
    }

    public static class CFXmlParameters {
        public CFXmlParameters(Configuration cfg, HttpServerRequest req, RoutingContext rc) {
            this.cfg = cfg;
            this.req = req;
            this.rc = rc;
        }
        public final Configuration cfg;
        public final HttpServerRequest req;
        public final RoutingContext rc;
    }

    public final ArrayList<CFXmlRenderer> array = new ArrayList<>();
}
