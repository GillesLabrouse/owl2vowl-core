package it.gov.innovazione.owl2vowl.model.entities.properties;

import it.gov.innovazione.owl2vowl.model.entities.HasReference;
import org.semanticweb.owlapi.model.IRI;

/** A data-property edge scoped to the class and datatype of a cardinality restriction. */
public class DatatypeCardinalityReference extends VowlDatatypeProperty implements HasReference {
    private IRI referenceIri;

    public DatatypeCardinalityReference(IRI iri, IRI referenceIri) {
        super(iri);
        this.referenceIri = referenceIri;
    }

    @Override public IRI getReferenceIri() { return referenceIri; }
    @Override public void setReferenceIri(IRI referenceIri) { this.referenceIri = referenceIri; }
}
