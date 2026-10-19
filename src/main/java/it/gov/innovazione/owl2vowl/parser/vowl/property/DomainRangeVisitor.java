package it.gov.innovazione.owl2vowl.parser.vowl.property;

import it.gov.innovazione.owl2vowl.model.data.VowlData;
import it.gov.innovazione.owl2vowl.model.entities.nodes.AbstractNode;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.NullClass;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.VowlClass;
import it.gov.innovazione.owl2vowl.model.entities.nodes.classes.VowlThing;
import it.gov.innovazione.owl2vowl.parser.owlapi.IndividualsVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.*;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eduard
 */
public class DomainRangeVisitor implements OWLObjectVisitorEx<AbstractNode> {
	@SuppressWarnings("unused")
	private final OWLProperty owlObjectProperty;
	private final VowlData vowlData;
	private Logger logger = LogManager.getLogger(DomainRangeVisitor.class);

	public DomainRangeVisitor(OWLProperty owlObjectProperty, VowlData vowlData) {
		this.owlObjectProperty = owlObjectProperty;
		this.vowlData = vowlData;
	}

	@Nonnull
	@Override
	public AbstractNode doDefault(@Nonnull Object object) {
		logger.info("Missed object for domain/range: " + object);
		return new NullClass();
	}

	@Override
	public AbstractNode visit(OWLClass ce) {
		if (ce.isOWLThing()) {
			return vowlData.getGenerator().generateThing();
		}
		if (ce.isOWLNothing()) {
			return new NullClass();
		}
		return vowlData.getClassForIri(ce.getIRI());
	}

	@Nonnull
	@Override
	public AbstractNode visit(@Nonnull OWLObjectIntersectionOf ce) {
		Set<OWLClassExpression> operands = ce.operands().collect(Collectors.toSet());
		Set<IRI> iriList = new HashSet<>();

		for (OWLClassExpression operand : operands) {
			if (operand.isOWLThing()) {
				continue;
			}
			AbstractNode node = operand.accept(this);
			if (node instanceof NullClass) {
				return new NullClass();
			}
			iriList.add(node.getIri());
		}

		if (iriList.contains(VowlThing.GENERIC_THING_IRI)) {
			iriList.remove(VowlThing.GENERIC_THING_IRI);
		}
		if (iriList.isEmpty()) {
			return vowlData.getGenerator().generateThing();
		}

		VowlClass intersection = vowlData.getSearcher().getIntersection(iriList);

		if (intersection == null) {
			intersection = vowlData.getGenerator().generateIntersection(iriList);
		}

		
		// memCleaner;
		iriList.clear();
		iriList=null;
		operands=null;
		return intersection;
	}

	@Nonnull
	@Override
	public AbstractNode visit(OWLObjectUnionOf ce) {
		Set<OWLClassExpression> operands = ce.operands().collect(Collectors.toSet());
		Set<IRI> iriList = new HashSet<>();

		for (OWLClassExpression operand : operands) {
			if (operand.isOWLThing()) {
				return vowlData.getGenerator().generateThing();
			}
			AbstractNode node = operand.accept(this);
			if (node instanceof NullClass) {
				return new NullClass();
			}
			iriList.add(node.getIri());
		}

		if (iriList.contains(VowlThing.GENERIC_THING_IRI)) {
			iriList.remove(VowlThing.GENERIC_THING_IRI);
		}

		VowlClass intersection = vowlData.getSearcher().getUnion(iriList);

		if (intersection == null) {
			intersection = vowlData.getGenerator().generateUnion(iriList);
		}

		// memCleaners
		iriList.clear();
		iriList=null;
		operands=null;
		
		return intersection;
	}

	@Nonnull
	@Override
	public AbstractNode visit(OWLObjectComplementOf ce) {
		OWLClassExpression baseClass = ce.getOperand();
		AbstractNode operand = baseClass.accept(this);
		if (operand instanceof NullClass) {
			return new NullClass();
		}

		return vowlData.getGenerator().generateComplement(operand.getIri());
	}

	@Nonnull
	@Override
	public AbstractNode visit(@Nonnull OWLDatatype node) {
		return vowlData.getGenerator().generateDatatypeReference(node.getIRI());
	}

	@Nonnull
	@Override
	public AbstractNode visit(OWLObjectOneOf ce) {
		VowlClass oneOfClass = vowlData.getGenerator().generateAnonymousClass();

		ce.individuals().collect(Collectors.toSet()).forEach(owlIndividual -> {
			owlIndividual.accept(new IndividualsVisitor(vowlData, vowlData.getOwlManager(), oneOfClass.getIri()));
		});

		return oneOfClass;
	}

}
