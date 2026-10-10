import AppKit
import Foundation
import Vision

guard CommandLine.arguments.count == 2 else { exit(2) }
let url = URL(fileURLWithPath: CommandLine.arguments[1])
let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = false
request.recognitionLanguages = ["en-US"]
let handler = VNImageRequestHandler(url: url)
do {
    try handler.perform([request])
    let observations = (request.results ?? []).compactMap { $0 as? VNRecognizedTextObservation }
    let sorted = observations.sorted(by: { (left: VNRecognizedTextObservation, right: VNRecognizedTextObservation) -> Bool in
        left.boundingBox.midX < right.boundingBox.midX
    })
    let text = sorted.compactMap { $0.topCandidates(1).first?.string }.joined()
    print(text)
} catch { exit(1) }
