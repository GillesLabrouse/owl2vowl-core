# Restriction conversion and WIDOCO compatibility

The development version 1.1.0-SNAPSHOT preserves the existing Java 11/OWLAPI 5.1.18
API and WebVOWL JSON format. Published 1.0.1 artifacts and tags remain unchanged.

## Corrected cases

- Zero is serialized as the string `"0"`; unspecified cardinalities remain absent.
- Qualified object/data cardinalities use reference edges with the original
  property IRI/labels and their restricting class and class/datatype filler.
  Compatible minimum/maximum bounds on the same edge are combined; different
  classes and fillers remain separate. General property declarations are retained.
- Object some/all restrictions and qualified cardinalities accept boolean class
  fillers. Intersections, unions and complements retain their operator nodes and
  exact operands, including nested boolean expressions.
- Opaque IRIs no longer assume a slash-delimited path. Plain URNs retain their
  namespaces, and imported URN entities remain distinguishable from local entities.

## Verification

```sh
./mvnw --batch-mode clean install
```

The JUnit suite covers classes, properties, local imports, UTF-8 labels, repeated
conversion, caller-owned ontology preservation, zero and qualified cardinalities,
scoped bounds, boolean fillers and URNs. It produces JSON fixtures under
`target/compatibility/`. The restriction regression tests fail against 1.0.1.

CI runs these tests on Java 11, 17 and 21. An additional integration job:

1. Builds WIDOCO at `d55952f71abdde5c8e590873a4eae662e5254363`, changing only its
   converter dependency to this checkout's version, and runs its original tests.
2. Compares eight complete JSON graphs against the checksum-pinned 1.0.1 artifact,
   normalizing traversal IDs and set ordering. These previously supported cases
   must remain identical; corrected cases have separate semantic assertions.
3. Extracts WebVOWL from the actual WIDOCO JAR and verifies its assets against the
   unchanged WIDOCO sources. Playwright checks rendered cardinalities and boolean
   operators, browser errors, and JSON/SVG exports with external requests blocked.
4. Generates documentation through WIDOCO's CLI and opens its generated
   `webvowl/data/ontology.json` in the embedded viewer.
5. Scans the library, its dependency graph and the actual WIDOCO JAR with OSV and
   Trivy, retaining all severities and using an empty ignore file. Java package
   detection is mandatory. These scans concern Java, not the viewer's JavaScript.

The pinned WIDOCO revision uses jsoup 1.23.1, independently flagged HIGH for
`CVE-2026-75140` by the October 19, 2026 Trivy database. CI retains that unfiltered
stock-JAR report and checksum. It then builds and tests a separate security variant
with jsoup 1.23.2 and gates that final JAR against HIGH/CRITICAL/UNKNOWN findings.
The modified POM and both JARs/reports are retained, so the upstream alert is not
suppressed or confused with the corrected variant. Deploying WIDOCO under a
zero-HIGH/CRITICAL policy also requires this independent WIDOCO dependency update;
changing the converter does not change jsoup. The jsoup project records the fix in
[release 1.23.2](https://github.com/jhy/jsoup/releases/tag/jsoup-1.23.2).
The independent [one-line WIDOCO patch](widoco-jsoup-1.23.2.patch) can be applied
to the pinned WIDOCO checkout without changing its converter version.

For an existing WIDOCO checkout, the packaged-viewer checks can also run locally:

```sh
python scripts/extract-webvowl.py /path/to/widoco.jar /path/to/widoco/src/main/resources/webvowl_1.1.7_patched target/viewer
node scripts/browser-compatibility.cjs target/viewer target/compatibility target/browser.json
```

Install Playwright 1.62.1 and its Chromium browser separately, or select an installed
browser through `PLAYWRIGHT_CHANNEL`. Neither is a converter runtime dependency.
CI retains fixtures, comparison results, browser reports, the scanned JAR,
checksums, source revisions and security database versions as artifacts.

## Remaining limits

The viewer is a diagram, not a complete OWL semantics engine. Inverse-property
qualified cardinalities, datatype facets, arbitrary nested restrictions and
general class axioms remain unsupported. Unsupported fillers are skipped rather
than having their cardinalities applied to the whole property. Inconsistent
cardinalities are not diagnosed; use an OWL reasoner for logical validation.
