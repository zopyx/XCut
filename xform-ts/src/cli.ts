import fs from "fs";
import { Parser } from "./parser";
import { evalModule, serializeItem } from "./eval";
import { parseXml } from "./xmlmodel";

function main(): void {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    process.stderr.write("Usage: xform <input.xml> <transform.xform>\n");
    process.exit(1);
  }
  const [inputPath, xformPath] = args;
  const xmlText = fs.readFileSync(inputPath, "utf-8");
  const xformText = fs.readFileSync(xformPath, "utf-8");
  const doc = parseXml(xmlText);
  const module = new Parser(xformText).parseModule();
  const result = evalModule(module, doc);
  const output = result.map((item) => serializeItem(item)).join("");
  process.stdout.write(output + "\n");
}

if (require.main === module) {
  main();
}

export { main };
