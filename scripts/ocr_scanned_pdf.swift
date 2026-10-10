import Foundation
import Vision
import ImageIO
import CoreGraphics

struct OCRLine: Codable {
    let text: String
    let confidence: Float
    let x: Double
    let y: Double
    let width: Double
    let height: Double
}

struct OCRPage: Codable {
    let pdf_page: Int
    let image: String
    let lines: [OCRLine]
}

func fail(_ message: String) -> Never {
    fputs("ERROR: \(message)\n", stderr)
    exit(2)
}

let args = Array(CommandLine.arguments.dropFirst())
guard args.count == 2 else { fail("usage: ocr_scanned_pdf.swift <rendered-page-dir> <output-json>") }
let inputDir = URL(fileURLWithPath: args[0], isDirectory: true)
let outputURL = URL(fileURLWithPath: args[1])
let files = try! FileManager.default.contentsOfDirectory(at: inputDir, includingPropertiesForKeys: nil)
    .filter { ["png", "jpg", "jpeg", "tif", "tiff"].contains($0.pathExtension.lowercased()) }
    .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
guard !files.isEmpty else { fail("no page images in \(inputDir.path)") }

let revision = VNRecognizeTextRequest.currentRevision
let languages = ["zh-Hans", "en-US"]
fputs("Vision revision \(revision), requested languages: \(languages.joined(separator: ", ")), pages: \(files.count)\n", stderr)

var pages: [OCRPage] = []
for (index, file) in files.enumerated() {
    guard let source = CGImageSourceCreateWithURL(file as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fputs("WARN: cannot open \(file.lastPathComponent)\n", stderr)
        continue
    }
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.recognitionLanguages = languages
    request.usesLanguageCorrection = false
    request.minimumTextHeight = 0.0015
    do {
        try VNImageRequestHandler(cgImage: image).perform([request])
    } catch {
        fputs("WARN: OCR failed on \(file.lastPathComponent): \(error)\n", stderr)
        continue
    }
    let observations = (request.results ?? []) as? [VNRecognizedTextObservation] ?? []
    let lines = observations.compactMap { observation -> OCRLine? in
        guard let candidate = observation.topCandidates(1).first else { return nil }
        let box = observation.boundingBox
        return OCRLine(text: candidate.string, confidence: candidate.confidence,
                       x: Double(box.origin.x), y: Double(box.origin.y),
                       width: Double(box.width), height: Double(box.height))
    }.sorted {
        let dy = abs($0.y - $1.y)
        if dy > max($0.height, $1.height) * 0.55 { return $0.y > $1.y }
        return $0.x < $1.x
    }
    pages.append(OCRPage(pdf_page: index + 1, image: file.lastPathComponent, lines: lines))
    fputs("page \(index + 1)/\(files.count): \(lines.count) OCR regions\n", stderr)
}
let encoder = JSONEncoder()
encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
do { try encoder.encode(pages).write(to: outputURL, options: .atomic) }
catch { fail("cannot write output: \(error)") }
