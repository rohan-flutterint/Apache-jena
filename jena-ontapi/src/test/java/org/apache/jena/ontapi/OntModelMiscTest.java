/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
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

package org.apache.jena.ontapi;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontapi.impl.GraphListenerBase;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntDisjoint;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.ontapi.utils.OntModels;
import org.apache.jena.rdf.listeners.StatementListener;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class OntModelMiscTest {
    @Test
    public void testRecursionOnComplementOf() {
        // test there is no StackOverflowError
        Assertions.assertThrows(OntJenaException.Recursion.class, () -> {
            Model m = OntModelFactory.createDefaultModel().setNsPrefixes(OntModelFactory.STANDARD);
            Resource anon = m.createResource().addProperty(RDF.type, OWL2.Class);
            anon.addProperty(OWL2.complementOf, anon);
            OntModel ont = OntModelFactory.createModel(m.getGraph());
            List<OntClass> ces = ont.ontObjects(OntClass.class).toList();
            Assertions.assertEquals(0, ces.size());
        });
    }

    @Test
    public void testCheckCreate() {
        OntModel m = OntModelFactory.createModel();
        List<Triple> triples = new ArrayList<>();
        m.getGraph().getEventManager().register(new GraphListenerBase() {
            @Override
            protected void addTripleEvent(Graph g, Triple t) {
                triples.add(t);
            }

            @Override
            protected void deleteTripleEvent(Graph g, Triple t) {
                Assertions.fail();
            }
        });
        m.createObjectUnionOf(m.getOWLThing());
        Assertions.assertEquals(4, triples.size());

        UnionGraph ug = (UnionGraph) m.getGraph();
        Assertions.assertEquals(0L, ug.superGraphs().count());
    }

    @Test
    public void testWriteAll() {
        OntModel a = OntModelFactory.createModel();
        a.createOntClass("A");
        OntModel b = OntModelFactory.createModel().setID("http://ont#B").getModel();
        b.createOntClass("B");
        a.addImport(b);

        ByteArrayOutputStream res1 = new ByteArrayOutputStream();
        a.writeAll(res1, "ttl");
        Model c = ModelFactory.createDefaultModel();
        c.read(new ByteArrayInputStream(res1.toByteArray()), "http://ex1#", "ttl");

        Assertions.assertEquals(5, c.size());
        Assertions.assertTrue(c.contains(c.createResource("http://ex1/A"), RDF.type, OWL2.Class));
        Assertions.assertTrue(c.contains(c.createResource("http://ex1/B"), RDF.type, OWL2.Class));

        ByteArrayOutputStream res2 = new ByteArrayOutputStream();
        a.writeAll(res2, "ttl", "http://ex2#");
        Model d = ModelFactory.createDefaultModel();
        d.read(new ByteArrayInputStream(res2.toByteArray()), "http://ex1#", "ttl");

        Assertions.assertEquals(5, d.size());
        Assertions.assertTrue(d.contains(d.createResource("http://ex2/A"), RDF.type, OWL2.Class));
        Assertions.assertTrue(d.contains(d.createResource("http://ex2/B"), RDF.type, OWL2.Class));
    }

    @Test
    public void testOntDisjointProperties() {
        var m = ModelFactory.createDefaultModel().setNsPrefixes(PrefixMapping.Standard);
        var p1 = m.createResource("p1", OWL.DatatypeProperty);
        var p2 = m.createResource("p2", OWL.DatatypeProperty);
        var d = m.createResource().addProperty(RDF.type, OWL.AllDisjointProperties);
        var list = m.createList(p1, p2);
        d.addProperty(OWL.members, list);

        var ont = OntModelFactory.createModel(m.getGraph());
        var actual1 = ont.ontObjects(OntDisjoint.DataProperties.class).toList();
        Assertions.assertEquals(1, actual1.size());

        var actual2 = ont.ontObjects(OntDisjoint.ObjectProperties.class).toList();
        Assertions.assertEquals(0, actual2.size());

        var actual3 = ont.ontObjects(OntDisjoint.class).toList();
        Assertions.assertEquals(1, actual3.size());
        Assertions.assertEquals(OntDisjoint.DataProperties.class, OntModels.getOntType(actual3.get(0)));

        Assertions.assertThrows(IllegalArgumentException.class, ont::createDisjointObjectProperties);
        Assertions.assertThrows(IllegalArgumentException.class, ont::createDisjointDataProperties);
    }

    @Test
    public void testOntDisjointClasses() {
        var m = ModelFactory.createDefaultModel().setNsPrefixes(PrefixMapping.Standard);
        var d = m.createResource().addProperty(RDF.type, OWL.AllDisjointClasses);
        var list = m.createList();
        d.addProperty(OWL.members, list);

        var ont = OntModelFactory.createModel(m.getGraph());
        var actual1 = ont.ontObjects(OntDisjoint.Classes.class).toList();
        Assertions.assertEquals(0, actual1.size());

        Assertions.assertThrows(IllegalArgumentException.class, ont::createDisjointClasses);
    }

    @Test
    public void testOntDisjointIndividuals() {
        var m = ModelFactory.createDefaultModel().setNsPrefixes(PrefixMapping.Standard);
        var d = m.createResource().addProperty(RDF.type, OWL.AllDifferent);
        var list = m.createList();
        d.addProperty(OWL.members, list);

        var ont = OntModelFactory.createModel(m.getGraph());
        var actual1 = ont.ontObjects(OntDisjoint.Individuals.class).toList();
        Assertions.assertEquals(0, actual1.size());

        Assertions.assertThrows(IllegalArgumentException.class, ont::createDifferentIndividuals);
    }

    @ParameterizedTest
    @EnumSource(names = {
            "OWL2_DL_MEM_RDFS_BUILTIN_INF",
            "OWL2_DL_MEM",
            "OWL2_DL_MEM_RDFS_INF",
            "OWL2_DL_MEM_TRANS_INF",
            "OWL2_DL_MEM_RULES_INF",
            "OWL2_MEM",
            "OWL2_MEM_RDFS_INF",
            "OWL2_MEM_TRANS_INF",
            "OWL2_MEM_RULES_INF",
            "OWL2_MEM_MINI_RULES_INF",
            "OWL2_MEM_MICRO_RULES_INF",
            "OWL2_EL_MEM",
            "OWL2_EL_MEM_RDFS_INF",
            "OWL2_EL_MEM_TRANS_INF",
            "OWL2_EL_MEM_RULES_INF",
            "OWL2_QL_MEM",
            "OWL2_QL_MEM_RDFS_INF",
            "OWL2_QL_MEM_TRANS_INF",
            "OWL2_QL_MEM_RULES_INF",
            "OWL2_RL_MEM",
            "OWL2_RL_MEM_RDFS_INF",
            "OWL2_RL_MEM_TRANS_INF",
            "OWL2_RL_MEM_RULES_INF",
            "OWL1_DL_MEM",
            "OWL1_DL_MEM_RDFS_INF",
            "OWL1_DL_MEM_TRANS_INF",
            "OWL1_DL_MEM_RULES_INF",
            "OWL1_MEM",
            "OWL1_MEM_RDFS_INF",
            "OWL1_MEM_TRANS_INF",
            "OWL1_MEM_RULES_INF",
            "OWL1_MEM_MINI_RULES_INF",
            "OWL1_MEM_MICRO_RULES_INF",
            "OWL1_LITE_MEM",
            "OWL1_LITE_MEM_RDFS_INF",
            "OWL1_LITE_MEM_TRANS_INF",
            "OWL1_LITE_MEM_RULES_INF",
            "RDFS_MEM",
            "RDFS_MEM_RDFS_INF",
            "RDFS_MEM_TRANS_INF",
    })
    public void testModelChangeListenerGH2868(TestSpec spec) {
        var listener = new TestModelChangedListener();
        var m = OntModelFactory.createModel(spec.inst).register(listener);

        var type1 = m.createOntClass("http://x1");
        type1.removeProperties();
        Assertions.assertEquals(1, listener.addedStatements.size());
        Assertions.assertEquals(1, listener.removedStatements.size());
        Assertions.assertEquals(1, listener.events.size());

        listener.clear();

        var type2 = m.createOntClass("http://x2");
        m.remove(type2.getMainStatement());
        Assertions.assertEquals(1, listener.addedStatements.size());
        Assertions.assertEquals(1, listener.removedStatements.size());
        Assertions.assertEquals(0, listener.events.size());

        listener.clear();

        m.createOntClass("http://x3");
        m.createOntClass("http://x4");
        m.removeAll(null, RDF.type, null);
        Assertions.assertEquals(2, listener.addedStatements.size());
        Assertions.assertEquals(2, listener.removedStatements.size());
        Assertions.assertEquals(1, listener.events.size());
    }

    private static class TestModelChangedListener extends StatementListener {
        private final List<Statement> addedStatements = new ArrayList<>();
        private final List<Statement> removedStatements = new ArrayList<>();
        private final List<Object> events = new ArrayList<>();

        @Override
        public void addedStatement(Statement x) {
            addedStatements.add(x);
        }

        @Override
        public void removedStatement(Statement x) {
            removedStatements.add(x);
        }

        @Override
        public void notifyEvent(Model m, Object event) {
            events.add(event);
        }

        private void clear() {
            addedStatements.clear();
            removedStatements.clear();
            events.clear();
        }
    }
}
