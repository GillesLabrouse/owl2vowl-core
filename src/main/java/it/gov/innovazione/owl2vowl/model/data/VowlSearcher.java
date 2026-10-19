package it.gov.innovazione.owl2vowl.model.data;

import it.gov.innovazione.owl2vowl.constants.PropertyType;
import it.gov.innovazione.owl2vowl.model.entities.AbstractEntity;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.VowlClass;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.VowlThing;
import it.gov.innovazione.owl2vowl.model.entities.nodes.datatypes.VowlDatatype;
import it.gov.innovazione.owl2vowl.model.entities.nodes.datatypes.VowlLiteral;
import it.gov.innovazione.owl2vowl.model.entities.properties.TypeOfProperty;
import it.gov.innovazione.owl2vowl.model.entities.properties.VowlDatatypeProperty;
import it.gov.innovazione.owl2vowl.model.entities.properties.VowlObjectProperty;
import it.gov.innovazione.owl2vowl.model.individuals.VowlIndividual;
import it.gov.innovazione.owl2vowl.model.visitor.VowlElementVisitor;
import org.semanticweb.owlapi.model.IRI;

import java.util.*;

/**
 * @author Eduard
 */
public class VowlSearcher implements VowlElementVisitor {

	private final VowlData data;
	private int dataSize = 0;
	private Set<VowlClass> intersections = new HashSet<>();
	private Set<VowlClass> unions = new HashSet<>();
	private Set<VowlThing> things = new HashSet<>();
	private Map<IRI, Set<IRI>> disjointMapping = new HashMap<>();

	public VowlSearcher(VowlData data) {
		this.data = data;
		dataSize = data.getEntityMap().keySet().size();
	}

	public Set<VowlThing> getThings() {
		checkConsistenty();
		return Collections.unmodifiableSet(things);
	}

	protected void refresh() {
		intersections.clear();
		unions.clear();
		things.clear();
		disjointMapping.clear();

		for (AbstractEntity abstractEntity : data.getEntityMap().values()) {
			abstractEntity.accept(this);
		}
	}

	protected void checkConsistenty() {
		int currentSize = data.getEntityMap().keySet().size();
		if (currentSize != dataSize) {
			refresh();
			dataSize = currentSize;
		}
	}

	@Override
	public void visit(VowlThing vowlThing) {
		things.add(vowlThing);
	}

	@Override
	public void visit(VowlClass vowlClass) {
		if (vowlClass.getElementOfIntersection().size() != 0) {
			intersections.add(vowlClass);
		}

		if (vowlClass.getElementsOfUnion().size() != 0) {
			unions.add(vowlClass);
		}
	}

	@Override
	public void visit(VowlLiteral vowlLiteral) {

	}

	@Override
	public void visit(VowlDatatype vowlDatatype) {

	}

	@Override
	public void visit(VowlObjectProperty vowlObjectProperty) {
		if (vowlObjectProperty.getType().equals(PropertyType.DISJOINT)) {
			addDisjoint(vowlObjectProperty.getDomains().iterator().next(), vowlObjectProperty.getRanges().iterator().next());
		}
	}

	@Override
	public void visit(VowlDatatypeProperty vowlDatatypeProperty) {
		if (vowlDatatypeProperty.getType().equals(PropertyType.DISJOINT)) {
			addDisjoint(vowlDatatypeProperty.getDomains().iterator().next(), vowlDatatypeProperty.getRanges().iterator().next());
		}
	}

	@Override
	public void visit(VowlIndividual vowlIndividual) {

	}

	@Override
	public void visit(TypeOfProperty typeOfProperty) {

	}

	public boolean containsDisjoint(IRI... disjoint) {
		checkConsistenty();
		return disjointMapping.containsKey(disjoint[0]) && disjointMapping.get(disjoint[0]).contains(disjoint[1]);
	}

	public VowlClass getIntersection(Collection<IRI> intersectionIris) {
		checkConsistenty();
		Set<IRI> operands = new HashSet<>(intersectionIris);
		for (VowlClass intersection : intersections) {
			if (intersection.getElementOfIntersection().equals(operands)) {
				return intersection;
			}
		}

		return null;
	}

	public VowlClass getUnion(Set<IRI> iriList) {
		checkConsistenty();

		for (VowlClass union : unions) {
			if (union.getElementsOfUnion().equals(iriList)) {
				return union;
			}
		}

		return null;
	}

	protected void addDisjoint(IRI... disjoints) {
		if (!disjointMapping.containsKey(disjoints[0])) {
			disjointMapping.put(disjoints[0], new HashSet<>());
		}

		if (!disjointMapping.containsKey(disjoints[1])) {
			disjointMapping.put(disjoints[1], new HashSet<>());
		}

		disjointMapping.get(disjoints[0]).add(disjoints[1]);
		disjointMapping.get(disjoints[1]).add(disjoints[0]);
	}
}
