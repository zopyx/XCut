// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "xform-swift",
    platforms: [
        .macOS(.v12)
    ],
    products: [
        .library(name: "XForm", targets: ["XForm"]),
        .executable(name: "xform-swift", targets: ["xform-cli"])
    ],
    targets: [
        .target(name: "XForm"),
        .executableTarget(name: "xform-cli", dependencies: ["XForm"])
    ]
)
