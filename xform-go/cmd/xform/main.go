package main

import (
	"fmt"
	"os"

	xform "xform-go"
)

func main() {
	if len(os.Args) < 3 {
		fmt.Fprintln(os.Stderr, "Usage: xform <input.xml> <transform.xform>")
		os.Exit(1)
	}
	inputPath := os.Args[1]
	xformPath := os.Args[2]

	xmlBytes, err := os.ReadFile(inputPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	xformText, err := os.ReadFile(xformPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	doc, err := xform.ParseXMLBytes(xmlBytes)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	module := xform.NewParser(string(xformText)).ParseModule()
	result := xform.EvalModule(module, doc)
	out := ""
	for _, item := range result {
		out += xform.SerializeItem(item)
	}
	fmt.Println(out)
}
