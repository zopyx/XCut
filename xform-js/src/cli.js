"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.main = main;
const fs_1 = __importDefault(require("fs"));
const parser_1 = require("./parser");
const eval_1 = require("./eval");
const xmlmodel_1 = require("./xmlmodel");
function main() {
    const args = process.argv.slice(2);
    if (args.length < 2) {
        process.stderr.write("Usage: xform <input.xml> <transform.xform>\n");
        process.exit(1);
    }
    const [inputPath, xformPath] = args;
    const xmlText = fs_1.default.readFileSync(inputPath, "utf-8");
    const xformText = fs_1.default.readFileSync(xformPath, "utf-8");
    const doc = (0, xmlmodel_1.parseXml)(xmlText);
    const module = new parser_1.Parser(xformText).parseModule();
    const result = (0, eval_1.evalModule)(module, doc);
    const output = result.map((item) => (0, eval_1.serializeItem)(item)).join("");
    process.stdout.write(output + "\n");
}
if (require.main === module) {
    main();
}
