import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/** Runs identical ontology fixtures against either converter through reflection. */
public class ConverterFeatureProbe {
    public static void main(String[] args) throws Exception {
        String converterClass = args[0];
        String prefix = args.length > 2 ? args[2] : "https://example.org/audit#";
        Path output = Paths.get(args[1]);
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String,Object>> results = new ArrayList<>();
        String[] cases = {"named-some", "named-all", "anonymous-some", "anonymous-all",
            "object-exact-positive", "object-min-positive", "object-max-positive",
            "object-exact-zero", "object-min-zero", "object-max-zero",
            "qualified-exact", "qualified-min", "qualified-max",
            "data-exact-positive", "data-min-positive", "data-max-positive",
            "data-exact-zero", "data-min-zero", "data-max-zero"};
        for (String scenario : cases) {
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("scenario", scenario);
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory f = manager.getOWLDataFactory();
            OWLOntology ontology = manager.createOntology(IRI.create(prefix + scenario));
            OWLClass person = f.getOWLClass(IRI.create(prefix + "Person"));
            OWLClass target = f.getOWLClass(IRI.create(prefix + "Target"));
            OWLClass other = f.getOWLClass(IRI.create(prefix + "Other"));
            OWLObjectProperty property = f.getOWLObjectProperty(IRI.create(prefix + "property"));
            OWLDataProperty dataProperty = f.getOWLDataProperty(IRI.create(prefix + "dataProperty"));
            manager.addAxiom(ontology, f.getOWLDeclarationAxiom(person));
            manager.addAxiom(ontology, f.getOWLDeclarationAxiom(target));
            manager.addAxiom(ontology, f.getOWLDeclarationAxiom(other));
            manager.addAxiom(ontology, f.getOWLObjectPropertyDomainAxiom(property, person));
            manager.addAxiom(ontology, f.getOWLObjectPropertyRangeAxiom(property, target));
            manager.addAxiom(ontology, f.getOWLDataPropertyDomainAxiom(dataProperty, person));
            manager.addAxiom(ontology, f.getOWLDataPropertyRangeAxiom(dataProperty, f.getStringOWLDatatype()));
            OWLClassExpression restriction;
            int count = scenario.endsWith("zero") ? 0 : 2;
            OWLClassExpression filler = scenario.startsWith("anonymous") ? f.getOWLObjectIntersectionOf(target, other) : target;
            if (scenario.endsWith("some")) restriction = f.getOWLObjectSomeValuesFrom(property, filler);
            else if (scenario.endsWith("all")) restriction = f.getOWLObjectAllValuesFrom(property, filler);
            else if (scenario.startsWith("data-exact")) restriction = f.getOWLDataExactCardinality(count, dataProperty);
            else if (scenario.startsWith("data-min")) restriction = f.getOWLDataMinCardinality(count, dataProperty);
            else if (scenario.startsWith("data-max")) restriction = f.getOWLDataMaxCardinality(count, dataProperty);
            else {
                filler = scenario.startsWith("qualified") ? target : f.getOWLThing();
                if (scenario.contains("exact")) restriction = f.getOWLObjectExactCardinality(count, property, filler);
                else if (scenario.contains("min")) restriction = f.getOWLObjectMinCardinality(count, property, filler);
                else restriction = f.getOWLObjectMaxCardinality(count, property, filler);
            }
            manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(person, restriction));
            try {
                Class<?> entry = Class.forName(converterClass);
                Object converter = entry.getConstructor(OWLOntology.class).newInstance(ontology);
                String json = (String)entry.getMethod("getJsonAsString").invoke(converter);
                Files.writeString(output.resolve(scenario + ".json"), json, StandardCharsets.UTF_8);
                JsonNode root = mapper.readTree(json);
                List<String> types = new ArrayList<>();
                for (JsonNode node : root.path("property")) types.add(node.path("type").asText());
                result.put("propertyTypes", types);
                List<JsonNode> attributes = new ArrayList<>();
                String iri = prefix + (scenario.startsWith("data") ? "dataProperty" : "property");
                for (JsonNode node : root.path("propertyAttribute")) if (iri.equals(node.path("iri").asText())) attributes.add(node);
                result.put("propertyAttributes", attributes);
                result.put("classCount", root.path("class").size());
                boolean preserved;
                try { preserved = ontology.getOWLOntologyManager() == manager; }
                catch (IllegalStateException ex) { preserved = false; }
                result.put("callerOntologyPreserved", preserved);
            } catch (Throwable error) {
                if (error instanceof InvocationTargetException) error = error.getCause();
                result.put("error", error.toString());
            }
            results.add(result);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(), results);
        System.out.println("Recorded " + results.size() + " scenarios for " + converterClass);
    }
}
