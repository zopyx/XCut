package zopyx.xform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: xform <input.xml> <transform.xform>");
            System.exit(1);
        }
        try {
            byte[] xmlBytes = Files.readAllBytes(Path.of(args[0]));
            String xformText = Files.readString(Path.of(args[1]));
            XFormEngine.Node doc = XFormEngine.parseXMLBytes(xmlBytes);
            XFormEngine.Module module = new XFormEngine.Parser(xformText).parseModule();
            List<Object> result = XFormEngine.evalModule(module, doc);
            StringBuilder out = new StringBuilder();
            for (Object item : result) {
                out.append(XFormEngine.serializeItem(item));
            }
            System.out.println(out);
        } catch (Exception e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            System.exit(1);
        }
    }
}
