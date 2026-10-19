# Standalone OWL2VOWL core

Convert an OWLAPI ontology into the JSON used by WebVOWL. This Java library
contains no Spring Boot, HTTP server or frontend assets.

The source was extracted from [Team Digitale's WebVOWL fork at
`5cdef0946423a8a813a58f5c9b478467f68cbd9d`](https://github.com/teamdigitale/dati-semantic-WebVOWL/tree/5cdef0946423a8a813a58f5c9b478467f68cbd9d).
The original MIT license and source headers are retained. This repository is an
independent maintenance fork, not an official Team Digitale release.

The extraction updates dependencies, excludes server code, preserves caller-owned
ontologies during cleanup, and restricts annotation pre-parsing to annotation
assertions. Its own POM aligns HTTP bundles from OWLAPI/jsonld-java, without
depending on WIDOCO's version overrides.

## Use

Java 11 or newer and OWLAPI 5.1.18 are supported. Add the JitPack repository and
released dependency to your Maven project:

```xml
<repositories>
    <repository>
        <id>jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.github.silverspy</groupId>
        <artifactId>owl2vowl-core</artifactId>
        <version>1.0.1</version>
    </dependency>
</dependencies>
```

```java
import it.gov.innovazione.owl2vowl.Owl2Vowl;

String json = new Owl2Vowl(ontology).getJsonAsString();
```

The original Java package is kept for compatibility. The JAR is a normal Maven
library: its dependencies are described by its POM, not bundled inside it.
Use released versions rather than a moving branch.

## Build and validate

```sh
./mvnw --batch-mode clean install
./mvnw --batch-mode dependency:tree -DoutputFile=dependency-tree.txt
mkdir -p target/security
node scripts/security/tree-inventory.mjs dependency-tree.txt target/security/dependencies.json
node scripts/security/scan-osv.mjs target/security/dependencies.json target/security/osv.json --fail-on-vulnerability
```

CI tests Java 11, 17 and 21 and retains the library, sources, dependency tree,
OSV and unfiltered Trivy reports. Conversion tests cover classes, object/data
properties, named restrictions, positive unqualified cardinalities and imports.

## Known limitations

The release candidate `1.1.0` fixes explicit zero cardinalities,
qualified cardinalities with named class/datatype fillers, boolean class fillers
(including nested intersections/unions), and plain URN identifiers. Qualified
cardinalities use separate edges for their restricting class and filler, without
changing the property's general domain/range. These fixes are not in release 1.0.1.

Inverse-property cardinality restrictions, datatype facets, arbitrary nested
restrictions and general class axioms remain outside the supported conversion
subset. Conflicting cardinalities are not diagnosed. This library is not a
reasoner or a lossless OWL representation. See the
[compatibility checks](docs/conversion-compatibility.md) for scope and reproduction.

## Security fixes

This is a security-updated version of the OWL2VOWL converter. Its dependency
stack removes the obsolete libraries associated with these critical advisories:

- `jackson-mapper-asl`: unsafe deserialization in Codehaus Jackson
  ([CVE-2019-10202](https://github.com/advisories/GHSA-c27h-mcmw-48hv)).
- `collections-generic`: Java deserialization gadget chains that can enable
  arbitrary code execution
  ([CVE-2015-7501](https://github.com/advisories/GHSA-fjq5-5j5f-mvxh)).

The remaining runtime dependencies are updated and checked, including transitive
dependencies. Trivy and OSV reported no known Java vulnerabilities for release
1.0.1 and its resolved dependency graph at the scanned versions. The
[release](https://github.com/silverspy/owl2vowl-core/releases/tag/1.0.1) includes
the security reports, source artifacts and checksums. See [SECURITY.md](SECURITY.md)
for vulnerability reporting.
