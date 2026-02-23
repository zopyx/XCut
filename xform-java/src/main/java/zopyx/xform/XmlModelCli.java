package zopyx.xform;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class XmlModelCli {
    private XmlModelCli() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: XmlModelCli <cmd> <xml>");
            System.exit(1);
        }
        String cmd = args[0];
        String xml = args[1];
        try {
            XFormEngine.Node doc = XFormEngine.parseXMLBytes(xml.getBytes(StandardCharsets.UTF_8));
            switch (cmd) {
                case "summary" -> {
                    XFormEngine.Node root = doc.children.get(0);
                    String a = root.attrs.getOrDefault("a", "");
                    System.out.println(root.name + "|" + a + "|" + root.stringValue() + "|" + doc.stringValue());
                }
                case "copy-shallow-child-count" -> {
                    XFormEngine.Node root = doc.children.get(0);
                    XFormEngine.Node copy = XFormEngine.deepCopy(root, false);
                    System.out.println(copy.children.size());
                }
                case "iter-desc" -> {
                    XFormEngine.Node root = doc.children.get(0);
                    List<XFormEngine.Node> nodes = XFormEngine.iterDescendants(root);
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < nodes.size(); i++) {
                        if (i > 0) out.append(',');
                        out.append(nodes.get(i).name == null ? "" : nodes.get(i).name);
                    }
                    System.out.println(out);
                }
                case "serialize" -> System.out.println(XFormEngine.serialize(doc.children.get(0)));
                case "serialize-doc" -> System.out.println(XFormEngine.serialize(doc));
                default -> {
                    System.err.println("Unknown command: " + cmd);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            System.exit(1);
        }
    }
}
