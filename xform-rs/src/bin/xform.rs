use std::process;

use xform::{eval_module, serialize_items, Parser};
use xform::xmlmodel::parse_xml;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: xform <input.xml> <transform.xform>");
        process::exit(1);
    }
    let xml_path = &args[1];
    let xform_path = &args[2];

    let xml_text = std::fs::read_to_string(xml_path).unwrap_or_else(|e| {
        eprintln!("Error reading {}: {}", xml_path, e);
        process::exit(1);
    });
    let xform_text = std::fs::read_to_string(xform_path).unwrap_or_else(|e| {
        eprintln!("Error reading {}: {}", xform_path, e);
        process::exit(1);
    });

    let doc = match parse_xml(&xml_text) {
        Ok(d) => d,
        Err(e) => {
            eprintln!("XML parse error: {}", e);
            process::exit(1);
        }
    };

    let module = match Parser::new(&xform_text).parse_module() {
        Ok(m) => m,
        Err(e) => {
            eprintln!("XForm parse error: {}", e);
            process::exit(1);
        }
    };

    match eval_module(&module, doc) {
        Ok(items) => print!("{}", serialize_items(&items)),
        Err(e) => {
            eprintln!("Evaluation error: {}", e);
            process::exit(1);
        }
    }
}
