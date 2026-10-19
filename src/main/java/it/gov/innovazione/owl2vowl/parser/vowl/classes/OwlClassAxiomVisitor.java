package it.gov.innovazione.owl2vowl.parser.vowl.classes;

import it.gov.innovazione.owl2vowl.constants.PropertyAllSomeValue;
import it.gov.innovazione.owl2vowl.constants.VowlAttribute;
import it.gov.innovazione.owl2vowl.model.data.VowlData;
import it.gov.innovazione.owl2vowl.model.entities.nodes.AbstractNode;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.AbstractClass;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.NullClass;
import it.gov.innovazione.owl2vowl.model.entities.properties.AbstractProperty;
import it.gov.innovazione.owl2vowl.model.entities.properties.DatatypeValueReference;
import it.gov.innovazione.owl2vowl.model.entities.properties.ObjectValueReference;
import it.gov.innovazione.owl2vowl.parser.owlapi.IndividualsVisitor;
import it.gov.innovazione.owl2vowl.parser.vowl.property.DomainRangeVisitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
//OWLDisjointClassesAxiomImpl;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class OwlClassAxiomVisitor implements OWLObjectVisitor {

	private VowlData vowlData;
	private OWLClass owlClass;
	private Logger logger = LogManager.getLogger(OwlClassAxiomVisitor.class);

	
	public void destrucotre() {
		this.vowlData = null;
		this.owlClass = null;
		
	}
	
	public OwlClassAxiomVisitor(VowlData vowlData, OWLClass owlClass) {
		this.vowlData = vowlData;
		this.owlClass = owlClass;
	}

	@Override
	public void doDefault(Object object) {
		logger.info("Unsupported axiom: " + object);
	}

	@Override
	public void visit(OWLEquivalentClassesAxiom axiom) {
		// tested for memory release[x]
		if (axiom.namedClasses().collect(Collectors.toSet()).size() != 1) {
			createEquivalentClass(axiom); 
			return;
		}
	
		OWLClass referencedClass = axiom.namedClasses().collect(Collectors.toSet()).iterator().next();
		Set<OWLClassExpression> expressionsWithoutRefClass = axiom.getClassExpressionsMinus(referencedClass);
		for (OWLClassExpression anonymExpressions : expressionsWithoutRefClass) {
			anonymExpressions.accept(new OwlClassAxiomVisitor(vowlData, referencedClass));
		}
	}
	private void createEquivalentClass(OWLEquivalentClassesAxiom axiom) {
		
		AbstractClass topClass = vowlData.getClassForIri(owlClass.getIRI());
		for (OWLClassExpression owlClassExpression : axiom.getClassExpressionsMinus(owlClass)) {
			topClass.addEquivalentElement(owlClassExpression.asOWLClass().getIRI());
		}
		topClass.addAttribute(VowlAttribute.EQUIVALENT);
		topClass=null;
	}

	@Override
	public void visit(OWLSubClassOfAxiom axiom) {
		// tested for memory release[x]
		if (axiom.isGCI()) {
			// KNOWN LIMITATION: anonymous OWL class handling not fully specified
			logger.info("Anonym subclass: " + axiom);
			return;
		}
		OWLClass subClass = axiom.getSubClass().asOWLClass();
		AbstractClass vowlSubclass = vowlData.getClassForIri(subClass.getIRI());
		if (axiom.getSuperClass().isAnonymous()) {
			axiom.getSuperClass().accept(new OwlClassAxiomVisitor(vowlData, owlClass));
		} else {
			OWLClass superClass = axiom.getSuperClass().asOWLClass();
			AbstractClass vowlSuperClass = vowlData.getClassForIri(superClass.getIRI());
			vowlSubclass.addSuperEntity(vowlSuperClass.getIri());
			vowlSuperClass.addSubEntity(vowlSubclass.getIri());
			superClass     = null;
			vowlSuperClass = null; 
		}
		subClass=null;
		vowlSubclass=null;
	}

	@Override
	public void visit(OWLDisjointClassesAxiom axiom) {
		// tested for memory release[x]
		Collection<OWLDisjointClassesAxiom> odca= axiom.asPairwiseAxioms();
		for (OWLDisjointClassesAxiom pairwiseAxiom : odca) {
			IRI[] domainRange = new IRI[2];
			int index = 0;
			
			for (OWLClassExpression aClassEX : pairwiseAxiom.classExpressions().collect(Collectors.toSet())){
				OWLClass cls=aClassEX.asOWLClass();
				domainRange[index++] = cls.getIRI();
			}
			if (!vowlData.getSearcher().containsDisjoint(domainRange[0], domainRange[1])) {
				vowlData.getGenerator().generateDisjointProperty(domainRange[0], domainRange[1]);
			}
			domainRange=null;
		}
	}

	@Override
	public void visit(OWLDisjointUnionAxiom axiom) {
		if (axiom.getOWLClass().isAnonymous()) {
			logger.info("Disjoint Union base is anonym.");
			return;
		}
		
		AbstractClass baseClass = vowlData.getClassForIri(axiom.getOWLClass().getIRI());
		baseClass.addAttribute(VowlAttribute.DISJOINTUNION);
		for (OWLClassExpression disjointClassEx : axiom.classExpressions().collect(Collectors.toSet())){
			OWLClass cls=disjointClassEx.asOWLClass();
			baseClass.addDisjointUnion(cls.getIRI());
		}
		//axiom.classesInSignature() << creates memory leak
//		for (OWLClass disjointClass : axiom.getOWLDisjointClassesAxiom().classesInSignature().collect(Collectors.toSet())) {
//			baseClass.addDisjointUnion(disjointClass.getIRI());
//		}
		baseClass=null;
	}


	@Override
	public void visit(OWLObjectMinCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setMinCardinality(ce.isQualified()
				? Math.max(property.getMinCardinality(), ce.getCardinality()) : ce.getCardinality());
	}

	@Override
	public void visit(OWLDataExactCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setExactCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLDataMaxCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setMaxCardinality(ce.isQualified() && property.getMaxCardinality() >= 0
				? Math.min(property.getMaxCardinality(), ce.getCardinality()) : ce.getCardinality());
	}

	@Override
	public void visit(OWLDataMinCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setMinCardinality(ce.isQualified()
				? Math.max(property.getMinCardinality(), ce.getCardinality()) : ce.getCardinality());
	}

	private AbstractProperty cardinalityProperty(OWLDataCardinalityRestriction ce) {
		IRI property = ce.getProperty().asOWLDataProperty().getIRI();
		if (!ce.isQualified()) {
			return vowlData.getPropertyForIri(property);
		}
		if (!ce.getFiller().isOWLDatatype()) {
			logger.info("Unsupported cardinality datatype expression: " + ce);
			return null;
		}
		return vowlData.getGenerator().generateDatatypeCardinalityReference(
				property, owlClass.getIRI(), ce.getFiller().asOWLDatatype().getIRI());
	}

	private AbstractProperty cardinalityProperty(OWLObjectCardinalityRestriction ce) {
		OWLObjectProperty property = ce.getProperty().getNamedProperty();
		if (!ce.isQualified() || ce.getFiller().isOWLNothing()) {
			return vowlData.getPropertyForIri(property.getIRI());
		}
		if (ce.getProperty().isAnonymous()) {
			logger.info("Unsupported inverse-property cardinality: " + ce);
			return null;
		}
		AbstractNode range = ce.getFiller().accept(new DomainRangeVisitor(property, vowlData));
		if (range instanceof NullClass) {
			return null;
		}
		return vowlData.getGenerator().generateObjectCardinalityReference(
				property.getIRI(), owlClass.getIRI(), range.getIri());
	}

	@Override
	public void visit(OWLDataAllValuesFrom ce) {
		processDataValueRestriction(ce, PropertyAllSomeValue.ALL);
	}

	@Override
	public void visit(OWLDataSomeValuesFrom ce) {
		processDataValueRestriction(ce, PropertyAllSomeValue.SOME);
	}

	private void processDataValueRestriction(OWLQuantifiedDataRestriction ce, PropertyAllSomeValue value) {
		if (!ce.getFiller().isOWLDatatype()) {
			// DataValue range is not a datatype — not supported
			logger.info("DataValue range is not a datatype: " + ce);
			return;
		}

		OWLDatatype range = ce.getFiller().asOWLDatatype();
		OWLDataProperty restrictedProperty = ce.getProperty().asOWLDataProperty();
		DatatypeValueReference valueReference = vowlData.getGenerator().generateDatatypeValueReference(restrictedProperty.getIRI(), value);
		valueReference.addRange(vowlData.getGenerator().generateDatatypeReference(range.getIRI()).getIri());
		valueReference.addDomain(owlClass.getIRI());
		
		range=null;
		restrictedProperty =null;
		valueReference=null;
	}

	@Override
	public void visit(OWLDataHasValue ce) {
		logger.info(ce + " not supported yet.");
	}

	@Override
	public void visit(OWLObjectAllValuesFrom ce) {
		processObjectValueRestriction(ce, PropertyAllSomeValue.ALL);
	}

	@Override
	public void visit(OWLObjectSomeValuesFrom ce) {
		processObjectValueRestriction(ce, PropertyAllSomeValue.SOME);
	}

	private void processObjectValueRestriction(OWLQuantifiedObjectRestriction ce, PropertyAllSomeValue value) {
		OWLObjectProperty restrictedProperty = ce.getProperty().getNamedProperty();
		AbstractNode rangeClass = ce.getFiller().accept(new DomainRangeVisitor(restrictedProperty, vowlData));
		if (rangeClass instanceof NullClass) {
			return;
		}
		ObjectValueReference objectValueReference = vowlData.getGenerator().generateObjectValueReference(restrictedProperty.getIRI(), value);
		objectValueReference.addRange(rangeClass.getIri());
		objectValueReference.addDomain(owlClass.getIRI());
		
		rangeClass=null;
		restrictedProperty=null;
		objectValueReference=null;
		
	}

	@Override
	public void visit(OWLObjectMaxCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setMaxCardinality(ce.isQualified() && property.getMaxCardinality() >= 0
				? Math.min(property.getMaxCardinality(), ce.getCardinality()) : ce.getCardinality());
	}

	@Override
	public void visit(OWLObjectExactCardinality ce) {
		AbstractProperty property = cardinalityProperty(ce);
		if (property != null) property.setExactCardinality(ce.getCardinality());
	}
	@Override
	public void visit(OWLObjectUnionOf ce) {
		Set<OWLClassExpression> operands = ce.operands().collect(Collectors.toSet());
		AbstractNode node = vowlData.getClassForIri(owlClass.getIRI());

		for (OWLClassExpression operand : operands) {
			if (!operand.isAnonymous()) {
				node.addElementToUnion(operand.asOWLClass().getIRI());
				node.addAttribute(VowlAttribute.UNION);
			} else {
				// KNOWN LIMITATION: anonymous OWL class handling not fully specified
				logger.info("Anonymous exists in unions.");
			}
		}
		node=null;
	}

	@Override
	public void visit(OWLObjectComplementOf ce) {
		if (ce.getOperand().isAnonymous()) {
			logger.info("Anonymous operand in object complement of.");
			return;
		}

		IRI baseClassIri = ce.getOperand().asOWLClass().getIRI();
		IRI complementIri = owlClass.getIRI();
		// Complement direction: set on the complemented class, not the base class
		//vowlData.getClassForIri(baseClassIri).addComplement(complementIri);
		vowlData.getClassForIri(complementIri).addComplement(baseClassIri);
		vowlData.getClassForIri(complementIri).addAttribute(VowlAttribute.COMPLEMENT);
	}

	@Override
	public void visit(OWLObjectIntersectionOf ce) {
		Set<OWLClassExpression> operands = ce.operands().collect(Collectors.toSet());
		AbstractNode node = vowlData.getClassForIri(owlClass.getIRI());

		for (OWLClassExpression operand : operands) {
			if (!operand.isAnonymous()) {
				node.addElementToIntersection(operand.asOWLClass().getIRI());
				node.addAttribute(VowlAttribute.INTERSECTION);
			} else {
				// KNOWN LIMITATION: anonymous OWL class handling not fully specified
				logger.info("Anonymous exists in intersections. " + operand);
			}
		}
	}

	@Override
	public void visit(OWLObjectOneOf ce) {
		ce.individuals().collect(Collectors.toSet()).forEach(owlIndividual -> owlIndividual.accept(new IndividualsVisitor(vowlData, owlIndividual, owlClass, vowlData
				.getOwlManager())));
	}
}
