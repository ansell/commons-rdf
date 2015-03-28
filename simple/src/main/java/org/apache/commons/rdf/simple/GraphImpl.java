/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.rdf.simple;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.api.Triple;

/**
 * A simple, memory-based implementation of Graph.
 * <p>
 * {@link Triple}s in the graph are kept in a {@link Set}.
 * <p>
 * All Stream operations are performed using parallel and unordered directives.
 */
final class GraphImpl implements Graph {

	private static final int TO_STRING_MAX = 10;
	private final Set<Triple> triples = new LinkedHashSet<Triple>();
	private final SimpleRDFTermFactory factory;

	GraphImpl(SimpleRDFTermFactory simpleRDFTermFactory) {
		this.factory = simpleRDFTermFactory;
	}

	@Override
	public void add(BlankNodeOrIRI subject, IRI predicate, RDFTerm object) {
		BlankNodeOrIRI newSubject = (BlankNodeOrIRI) internallyMap(subject);
		IRI newPredicate = (IRI) internallyMap(predicate);
		RDFTerm newObject = internallyMap(object);
		triples.add(factory.createTriple(newSubject, newPredicate, newObject));
	}

	@Override
	public void add(Triple triple) {
		BlankNodeOrIRI newSubject = (BlankNodeOrIRI) internallyMap(triple
				.getSubject());
		IRI newPredicate = (IRI) internallyMap(triple.getPredicate());
		RDFTerm newObject = internallyMap(triple.getObject());
		// Check if any of the object references changed during the mapping, to
		// avoid creating a new Triple object if possible
		if (newSubject == triple.getSubject()
				&& newPredicate == triple.getPredicate()
				&& newObject == triple.getObject()) {
			triples.add(triple);
		} else {
			triples.add(factory.createTriple(newSubject, newPredicate,
					newObject));
		}
	}

	private <T extends RDFTerm> RDFTerm internallyMap(T object) {
		if (object instanceof BlankNode) {
			BlankNode blankNode = (BlankNode) object;
			// This guarantees that adding the same BlankNode multiple times to
			// this graph will generate a local object that is mapped to an
			// equivalent object, based on the code in the package private
			// BlankNodeImpl class
			return factory.createBlankNode(blankNode.internalIdentifier());
		} else if (object instanceof IRI && !(object instanceof IRIImpl)) {
			IRI iri = (IRI) object;
			return factory.createIRI(iri.getIRIString());
		} else if (object instanceof Literal
				&& !(object instanceof LiteralImpl)) {
			Literal literal = (Literal) object;
			if (literal.getLanguageTag().isPresent()) {
				return factory.createLiteral(literal.getLexicalForm(), literal
						.getLanguageTag().get());
			} else {
				return factory.createLiteral(literal.getLexicalForm(),
						(IRI) internallyMap(literal.getDatatype()));
			}
		} else {
			// The object is a local implementation, and is not a BlankNode, so
			// can be returned directly
			return object;
		}
	}

	@Override
	public void clear() {
		triples.clear();
	}

	@Override
	public boolean contains(BlankNodeOrIRI subject, IRI predicate,
			RDFTerm object) {
		return getTriples(subject, predicate, object).findFirst().isPresent();
	}

	@Override
	public boolean contains(Triple triple) {
		return triples.contains(Objects.requireNonNull(triple));
	}

	@Override
	public Stream<Triple> getTriples() {
		return triples.parallelStream();
	}

	@Override
	public Stream<Triple> getTriples(final BlankNodeOrIRI subject,
			final IRI predicate, final RDFTerm object) {
		Predicate<Triple> match = new Predicate<Triple>() {
			@Override
			public boolean test(Triple t) {
				// Lacking the requirement for .equals() we have to be silly
				// and test ntriples string equivalance
				if (subject != null
						&& !t.getSubject().equals(subject)) {
					return false;
				}
				if (predicate != null
						&& !t.getPredicate().equals(predicate)) {
					return false;
				}
				if (object != null
						&& !t.getObject().equals(object)) {
					return false;
				}
				return true;
			}
		};
		return getTriples(match);
	}

	@Override
	public Stream<Triple> getTriples(final Predicate<Triple> filter) {
		return getTriples().unordered().filter(filter);
	}

	@Override
	public void remove(BlankNodeOrIRI subject, IRI predicate, RDFTerm object) {
		Stream<Triple> toRemove = getTriples(subject, predicate, object);
		for (Triple t : toRemove.collect(Collectors.toList())) {
			// Avoid ConcurrentModificationException in ArrayList
			remove(t);
		}
	}

	@Override
	public void remove(Triple triple) {
		triples.remove(Objects.requireNonNull(triple));
	}

	@Override
	public long size() {
		return triples.size();
	}

	@Override
	public String toString() {
		String s = getTriples().limit(TO_STRING_MAX).map(Object::toString)
				.collect(Collectors.joining("\n"));
		if (size() > TO_STRING_MAX) {
			return s + "\n# ... +" + (size() - TO_STRING_MAX) + " more";
		} else {
			return s;
		}
	}

}
