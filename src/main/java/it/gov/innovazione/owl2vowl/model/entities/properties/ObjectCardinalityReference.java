package it.gov.innovazione.owl2vowl.model.entities.properties;

import it.gov.innovazione.owl2vowl.model.entities.HasReference;
import org.semanticweb.owlapi.model.IRI;

/** An object-property edge scoped to the class and filler of a cardinality restriction. */
public class ObjectCardinalityReference extends VowlObjectProperty implements HasReference {
    private IRI referenceIri;

    public ObjectCardinalityReference(IRI iri, IRI referenceIri) {
        super(iri);
        this.referenceIri = referenceIri;
    }

    @Override public IRI getReferenceIri() { return referenceIri; }
    @Override public void setReferenceIri(IRI referenceIri) { this.referenceIri = referenceIri; }
}
