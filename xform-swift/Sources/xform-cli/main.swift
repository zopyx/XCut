import Foundation
import XForm

if CommandLine.arguments.count < 3 {
    FileHandle.standardError.write(Data("Usage: xform <input.xml> <transform.xform>\n".utf8))
    exit(1)
}

let inputPath = CommandLine.arguments[1]
let xformPath = CommandLine.arguments[2]

let xmlText = try String(contentsOfFile: inputPath, encoding: .utf8)
let xformText = try String(contentsOfFile: xformPath, encoding: .utf8)

let doc = try parseXML(xmlText)
let module = Parser(xformText).parseModule()
let result = evalModule(module, doc)
let output = result.map { serializeItem($0) }.joined()
print(output)
