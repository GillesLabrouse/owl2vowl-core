import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.innovazione.owl2vowl.Owl2Vowl;
import it.gov.innovazione.owl2vowl.parser.helper.ComparisonHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import static org.junit.Assert.*;

public class RestrictionConversionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static class Fixture {
        final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        final OWLDataFactory f = manager.getOWLDataFactory();
        final OWLOntology ontology;
        final String base;
        final OWLClass parent, child, target, other;
        final OWLObjectProperty property;
        final OWLDataProperty data;

        Fixture() throws Exception { this("https://example.org/restrictions#"); }

        Fixture(String base) throws Exception {
            this.base = base;
            ontology = manager.createOntology(IRI.create(base + "ontology"));
            parent = type("Parent"); child = type("Child");
            target = type("Target"); other = type("Other");
            property = f.getOWLObjectProperty(IRI.create(base + "property"));
            data = f.getOWLDataProperty(IRI.create(base + "data"));
            add(f.getOWLObjectPropertyDomainAxiom(property, parent));
            add(f.getOWLObjectPropertyRangeAxiom(property, other));
            add(f.getOWLDataPropertyDomainAxiom(data, parent));
            add(f.getOWLDataPropertyRangeAxiom(data, f.getStringOWLDatatype()));
            add(f.getOWLAnnotationAssertionAxiom(f.getRDFSLabel(), property.getIRI(),
                    f.getOWLLiteral("related — café", "en")));
        }

        OWLClass type(String name) {
            OWLClass type = f.getOWLClass(IRI.create(base + name));
            add(f.getOWLDeclarationAxiom(type));
            return type;
        }

        void add(OWLAxiom axiom) { manager.addAxiom(ontology, axiom); }
        void restrict(OWLClassExpression expression) { add(f.getOWLSubClassOfAxiom(child, expression)); }

        JsonNode convert(String name) throws Exception {
            int count = ontology.getAxiomCount();
            String json = new Owl2Vowl(ontology).getJsonAsString();
            assertSame(manager, ontology.getOWLOntologyManager());
            assertEquals(count, ontology.getAxiomCount());
            JsonNode root = JSON.readTree(json);
            assertReferencesExist(root);
            Path output = Path.of("target/compatibility", name + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json);
            if (name.equals("scoped-bounds")) manager.saveOntology(ontology,
                    new org.semanticweb.owlapi.formats.RDFXMLDocumentFormat(),
                    IRI.create(output.resolveSibling(name + ".owl").toAbsolutePath().toUri()));
            return root;
        }
    }

    @Test public void distinguishesAbsentCardinalitiesFromExplicitZero() throws Exception {
        Fixture plain = new Fixture();
        JsonNode plainJson = plain.convert("unrestricted");
        for (JsonNode p : plainJson.path("propertyAttribute")) {
            for (String field : new String[]{"cardinality", "minCardinality", "maxCardinality"})
                assertEquals("", p.path(field).asText());
        }
        for (boolean data : new boolean[]{false, true}) {
            for (String kind : new String[]{"exact", "min", "max"}) {
                Fixture x = new Fixture();
                x.restrict(cardinality(x, kind, 0, data, false));
                JsonNode root = x.convert((data ? "data-" : "object-") + kind + "-zero");
                JsonNode p = properties(root, data ? x.data.getIRI() : x.property.getIRI()).get(0);
                assertEquals("0", p.path(field(kind)).asText());
                for (String other : new String[]{"exact", "min", "max"})
                    if (!kind.equals(other)) assertEquals("", p.path(field(other)).asText());
            }
        }
    }

    @Test public void keepsQualifiedObjectCardinalitiesScopedToTheirClassAndFiller() throws Exception {
        for (String kind : new String[]{"exact", "min", "max"}) {
            for (int count : new int[]{0, 2}) {
                Fixture x = new Fixture();
                x.restrict(cardinality(x, kind, count, false, true));
                JsonNode root = x.convert("qualified-" + kind + "-" + count);
                JsonNode scoped = edge(root, x.property.getIRI(), x.child.getIRI(), x.target.getIRI());
                assertEquals(Integer.toString(count), scoped.path(field(kind)).asText());
                assertEquals("related — café", scoped.path("label").path("en").asText());
                assertEquals("", edge(root, x.property.getIRI(), x.parent.getIRI(), x.other.getIRI())
                        .path(field(kind)).asText());
            }
        }
    }

    @Test public void keepsQualifiedDataCardinalitiesScopedToTheirClassAndDatatype() throws Exception {
        for (String kind : new String[]{"exact", "min", "max"}) {
          for (int count : new int[]{0, 2}) {
            Fixture x = new Fixture();
            x.restrict(cardinality(x, kind, count, true, true));
            JsonNode root = x.convert("qualified-data-" + kind + "-" + count);
            JsonNode scoped = edge(root, x.data.getIRI(), x.child.getIRI(), x.f.getIntegerOWLDatatype().getIRI());
            assertEquals(Integer.toString(count), scoped.path(field(kind)).asText());
            assertEquals("", edge(root, x.data.getIRI(), x.parent.getIRI(), x.f.getStringOWLDatatype().getIRI())
                    .path(field(kind)).asText());
          }
        }
    }

    @Test public void combinesBoundsOnlyForTheSameQualifiedRestriction() throws Exception {
        Fixture x = new Fixture();
        x.restrict(x.f.getOWLObjectMinCardinality(1, x.property, x.target));
        x.restrict(x.f.getOWLObjectMinCardinality(0, x.property, x.target));
        x.restrict(x.f.getOWLObjectMaxCardinality(3, x.property, x.target));
        x.restrict(x.f.getOWLObjectMaxCardinality(5, x.property, x.target));
        OWLClass sibling = x.type("Sibling");
        x.add(x.f.getOWLSubClassOfAxiom(sibling, x.f.getOWLObjectExactCardinality(4, x.property, x.target)));
        x.restrict(x.f.getOWLObjectExactCardinality(5, x.property, x.other));
        JsonNode root = x.convert("scoped-bounds");
        JsonNode first = edge(root, x.property.getIRI(), x.child.getIRI(), x.target.getIRI());
        assertEquals("1", first.path("minCardinality").asText());
        assertEquals("3", first.path("maxCardinality").asText());
        assertEquals("", first.path("cardinality").asText());
        assertEquals("4", edge(root, x.property.getIRI(), sibling.getIRI(), x.target.getIRI()).path("cardinality").asText());
        assertEquals("5", edge(root, x.property.getIRI(), x.child.getIRI(), x.other.getIRI()).path("cardinality").asText());
    }

    @Test public void representsAnonymousIntersectionFillersWithoutFlatteningThem() throws Exception {
        for (boolean all : new boolean[]{false, true}) {
            Fixture x = new Fixture();
            OWLClassExpression filler = x.f.getOWLObjectIntersectionOf(x.target, x.other);
            x.restrict(all ? x.f.getOWLObjectAllValuesFrom(x.property, filler)
                    : x.f.getOWLObjectSomeValuesFrom(x.property, filler));
            JsonNode root = x.convert(all ? "intersection-all" : "intersection-some");
            JsonNode p = properties(root, x.property.getIRI()).stream()
                    .filter(a -> a.path("domain").asText().equals(id(root, x.child.getIRI()))).findFirst().orElseThrow();
            JsonNode range = node(root, p.path("range").asText());
            assertEquals(Set.of(id(root, x.target.getIRI()), id(root, x.other.getIRI())), values(range.path("intersection")));
            assertEquals(all ? "owl:allValuesFrom" : "owl:someValuesFrom", propertyType(root, p));
        }
    }

    @Test public void resolvesNestedBooleanFillersAndKeepsDifferentIntersectionsDistinct() throws Exception {
        Fixture x = new Fixture();
        OWLClass extra = x.type("Extra");
        OWLClassExpression small = x.f.getOWLObjectIntersectionOf(x.target, x.other);
        OWLClassExpression large = x.f.getOWLObjectIntersectionOf(x.target, x.other, extra);
        x.restrict(x.f.getOWLObjectSomeValuesFrom(x.property, small));
        x.restrict(x.f.getOWLObjectAllValuesFrom(x.property, large));
        OWLClassExpression nested = x.f.getOWLObjectIntersectionOf(x.target,
                x.f.getOWLObjectUnionOf(x.other, extra));
        x.restrict(x.f.getOWLObjectExactCardinality(2, x.property, nested));
        JsonNode root = x.convert("nested-intersections");
        List<JsonNode> scoped = properties(root, x.property.getIRI()).stream()
                .filter(a -> a.path("domain").asText().equals(id(root, x.child.getIRI()))).collect(Collectors.toList());
        assertEquals(3, scoped.size());
        assertTrue(scoped.stream().map(a -> node(root, a.path("range").asText()).path("intersection").size()).anyMatch(n -> n == 3));
        JsonNode cardinality = scoped.stream().filter(a -> "2".equals(a.path("cardinality").asText())).findFirst().orElseThrow();
        JsonNode intersection = node(root, cardinality.path("range").asText());
        assertEquals(2, intersection.path("intersection").size());
        assertTrue(values(intersection.path("intersection")).stream().map(i -> node(root, i)).anyMatch(n -> n.path("union").size() == 2));
    }

    @Test public void convertsPlainUrnsAndPreservesImportedNamespaces() throws Exception {
        Fixture x = new Fixture("urn:example:main:");
        OWLOntology imported = x.manager.createOntology(IRI.create("urn:example:imported:ontology"));
        OWLClass external = x.f.getOWLClass(IRI.create("urn:example:imported:External"));
        x.manager.addAxiom(imported, x.f.getOWLDeclarationAxiom(external));
        x.manager.applyChange(new AddImport(x.ontology, x.f.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().orElseThrow())));
        x.restrict(x.f.getOWLObjectSomeValuesFrom(x.property, external));
        x.restrict(x.f.getOWLObjectExactCardinality(0, x.property, x.target));
        JsonNode root = x.convert("urn-imports");
        assertFalse(values(node(root, id(root, x.child.getIRI())).path("attributes")).contains("external"));
        assertTrue(values(node(root, id(root, external.getIRI())).path("attributes")).contains("external"));
        assertEquals(2, x.ontology.getImportsClosure().size());
        assertEquals(root, JSON.readTree(new Owl2Vowl(x.ontology).getJsonAsString()));
    }

    @Test public void extractsOpaqueNamespacesWithoutChangingHttpBases() {
        assertEquals("urn:example:", ComparisonHelper.extractBaseIRI("urn:example:Person"));
        assertEquals("https://example.org/model", ComparisonHelper.extractBaseIRI("https://example.org/model#Person"));
        assertEquals("https://example.org/model", ComparisonHelper.extractBaseIRI("https://example.org/model/Person"));
        assertFalse(ComparisonHelper.hasDifferentNameSpace("urn:example:main:Person", "urn:example:main:ontology"));
        assertTrue(ComparisonHelper.hasDifferentNameSpace("urn:example:imported:Person", "urn:example:main:ontology"));
        assertFalse(ComparisonHelper.hasDifferentNameSpace("urn:example:main#Person", "urn:example:main"));
        assertTrue(ComparisonHelper.hasDifferentNameSpace("urn:other:main#Person", "urn:example:main"));
    }

    @Test public void doesNotApplyUnsupportedQualifiedFillersToTheWholeProperty() throws Exception {
        Fixture x = new Fixture();
        x.restrict(x.f.getOWLObjectExactCardinality(2, x.property.getInverseProperty(), x.target));
        x.restrict(x.f.getOWLObjectSomeValuesFrom(x.property,
                x.f.getOWLObjectIntersectionOf(x.target, x.f.getOWLObjectHasSelf(x.property))));
        x.restrict(x.f.getOWLDataExactCardinality(2, x.data, x.f.getOWLDatatypeRestriction(
                x.f.getIntegerOWLDatatype(), org.semanticweb.owlapi.vocab.OWLFacet.MIN_INCLUSIVE, x.f.getOWLLiteral(10))));
        JsonNode root = x.convert("unsupported-fillers");
        assertEquals(1, properties(root, x.property.getIRI()).size());
        assertEquals(1, properties(root, x.data.getIRI()).size());
        assertEquals("", properties(root, x.property.getIRI()).get(0).path("cardinality").asText());
        assertEquals("", properties(root, x.data.getIRI()).get(0).path("cardinality").asText());
    }

    private static OWLClassExpression cardinality(Fixture x, String kind, int n, boolean data, boolean qualified) {
        if (data) {
            OWLDataRange filler = qualified ? x.f.getIntegerOWLDatatype() : x.f.getTopDatatype();
            if (kind.equals("min")) return x.f.getOWLDataMinCardinality(n, x.data, filler);
            if (kind.equals("max")) return x.f.getOWLDataMaxCardinality(n, x.data, filler);
            return x.f.getOWLDataExactCardinality(n, x.data, filler);
        }
        OWLClassExpression filler = qualified ? x.target : x.f.getOWLThing();
        if (kind.equals("min")) return x.f.getOWLObjectMinCardinality(n, x.property, filler);
        if (kind.equals("max")) return x.f.getOWLObjectMaxCardinality(n, x.property, filler);
        return x.f.getOWLObjectExactCardinality(n, x.property, filler);
    }

    private static String field(String kind) { return kind.equals("exact") ? "cardinality" : kind + "Cardinality"; }
    private static Set<String> values(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }
    private static List<JsonNode> properties(JsonNode root, IRI iri) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode p : root.path("propertyAttribute")) if (iri.toString().equals(p.path("iri").asText())) found.add(p);
        assertFalse("Missing property " + iri, found.isEmpty());
        return found;
    }
    private static String id(JsonNode root, IRI iri) {
        for (JsonNode n : root.path("classAttribute")) if (iri.toString().equals(n.path("iri").asText())) return n.path("id").asText();
        throw new AssertionError("Missing class/datatype " + iri);
    }
    private static JsonNode node(JsonNode root, String id) {
        for (JsonNode n : root.path("classAttribute")) if (id.equals(n.path("id").asText())) return n;
        throw new AssertionError("Missing node " + id);
    }
    private static JsonNode edge(JsonNode root, IRI property, IRI domain, IRI range) {
        return properties(root, property).stream().filter(p -> p.path("domain").asText().equals(id(root, domain))
                && p.path("range").asText().equals(id(root, range))).findFirst()
                .orElseThrow(() -> new AssertionError("Missing scoped edge " + domain + " -> " + range));
    }
    private static String propertyType(JsonNode root, JsonNode p) {
        for (JsonNode n : root.path("property")) if (n.path("id").equals(p.path("id"))) return n.path("type").asText();
        throw new AssertionError("Missing property type");
    }
    private static void assertReferencesExist(JsonNode root) {
        Set<String> ids = StreamSupport.stream(root.path("classAttribute").spliterator(), false)
                .map(n -> n.path("id").asText()).collect(Collectors.toSet());
        for (JsonNode p : root.path("propertyAttribute")) {
            assertTrue("Dangling domain " + p, ids.contains(p.path("domain").asText()));
            assertTrue("Dangling range " + p, ids.contains(p.path("range").asText()));
        }
        for (JsonNode n : root.path("classAttribute")) for (String field : new String[]{"intersection", "union", "complement"})
            assertTrue("Dangling " + field, ids.containsAll(values(n.path(field))));
    }
}
