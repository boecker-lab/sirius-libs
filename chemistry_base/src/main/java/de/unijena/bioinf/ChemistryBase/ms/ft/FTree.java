/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.ChemistryBase.ms.ft;

import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.graphUtils.tree.BackrefTreeAdapter;
import de.unijena.bioinf.graphUtils.tree.PostOrderTraversal;
import de.unijena.bioinf.graphUtils.tree.PreOrderTraversal;
import de.unijena.bioinf.graphUtils.tree.TreeCursor;

import java.util.*;

public class FTree extends AbstractFragmentationGraph {

    protected Fragment root;

    /**
     * The absolute score of this tree.
     * It is recommended to using tree.getAnnotationOrThrow(TreeScoring.class).getOverallScore() instead
     * This score is the raw result of the underlying optimization problem and might differ from the final
     * score (e.g. there might be orthogonal scores that are added later).
     */
    protected double treeWeight;

    public FTree(MolecularFormula rootFormula) {
        this.root = addFragment(rootFormula);
    }

    public FTree(FTree copy) {
        super(copy);
        this.root = fragments.get(0);
        assert root.isRoot();
    }

    /**
     * Add a new root to the tree and connecting it with the previous root
     * @param newRoot
     */
    public Fragment addRoot(MolecularFormula newRoot) {
        final MolecularFormula loss = newRoot.subtract(root.getFormula());
        if (!loss.isAllPositiveOrZero()) {
            throw new IllegalArgumentException(root.getFormula().toString() + " cannot be child formula of " + newRoot.toString());
        }
        final Fragment f = addFragment(newRoot);
        addLoss(f, root);
        fragments.set(0, f);
        fragments.set(f.vertexId, root);
        root.setVertexId(f.vertexId);
        f.setVertexId(0);
        root = f;
        return f;
    }

    public double getTreeWeight() {
        return treeWeight;
    }

    public void setTreeWeight(double weight) {
        this.treeWeight = weight;
    }

    /*
    public void swapRoot(Fragment f) {
        if (!isOwnFragment(f)) throw new IllegalArgumentException("Expect a fragment of the same tree as parameter");
        fragments.set(0, f);
        fragments.set(f.vertexId, root);
        root.setVertexId(f.vertexId);
        f.setVertexId(0);
        invertPathsForRootSwapping(f);
        root = f;
    }

    protected void invertPathsForRootSwapping(Fragment v) {
        // all incoming egdes become outgoing edges
        final ArrayList<Loss> incomingLosses = new ArrayList<Loss>(v.getIncomingEdges());
        for (Loss l : incomingLosses) {
            final Fragment u = l.getSource();
            // u becomes child of v
            invertPathsForRootSwapping(u);
            // delete edge u->v
            deleteLoss(getLoss(u, v));
            // add new edge v<-u
            swapLoss(u, v);
        }
    }
    */

    public static BackrefTreeAdapter<Fragment> treeAdapter() {
        return new BackrefTreeAdapter<Fragment>() {
            @Override
            public Fragment getParent(Fragment node) {
                return node.inDegree == 0 ? null : node.getParent();
            }

            @Override
            public int indexOfSibling(Fragment node) {
                return node.getIncomingEdge().sourceEdgeOffset;
            }

            @Override
            public int getDepth(Fragment node) {
                int d = 0;
                while (!node.isRoot()) {
                    ++d;
                    node = node.getParent();
                }
                return d;
            }

            @Override
            public int getDegreeOf(Fragment vertex) {
                return vertex.getOutDegree();
            }

            @Override
            public List<Fragment> getChildrenOf(Fragment vertex) {
                return vertex.getChildren();
            }
        };
    }

    public Fragment addFragment(Fragment parent, MolecularFormula child) {
        final MolecularFormula loss = parent.formula.subtract(child);
        if (!loss.isAllPositiveOrZero()) {
            throw new IllegalArgumentException(child.toString() + " cannot be child formula of " + parent.formula.toString());
        }
        final Fragment f = addFragment(child);
        addLoss(parent, f);
        return f;
    }

    /**
     * Next to adding new fragments and contracting edges this is the only allowed modification of fragmentation trees:
     * It removes the incoming edge of vertex and add a new edge from newParent to vertex
     *
     * @param vertex
     * @param newParent
     * @return the newly created edge
     */
    public Loss swapLoss(Fragment vertex, Fragment newParent) {
        super.deleteLoss(vertex.getIncomingEdge());
        return super.addLoss(newParent, vertex);
    }

    /**
     * Delete a vertex, connect all its children to its parent
     *
     * @param vertex
     * @return number of newly created edges
     */
    public int deleteVertex(Fragment vertex) {
        final List<Fragment> children = new ArrayList<Fragment>(vertex.getChildren());
        final Fragment parent = vertex.getParent();
        deleteFragment(vertex);
        for (Fragment c : children) addLoss(parent, c);
        return children.size();
    }

    public int deleteSubtree(Fragment root) {
        assert !root.isDeleted();
        final Iterator<Fragment> iter = postOrderIterator(root);
        final ArrayList<Fragment> vertices = new ArrayList<Fragment>();
        while (iter.hasNext()) {
            vertices.add(iter.next());
        }
        for (Fragment f : vertices) {
            deleteFragment(f);
        }
        return vertices.size();
    }

    @Override
    public Iterator<Fragment> postOrderIterator(Fragment startingRoot) {
        return PostOrderTraversal.createSubtreeTraversal(startingRoot, FTree.treeAdapter()).iterator();
    }

    @Override
    public Iterator<Fragment> preOrderIterator(Fragment startingRoot) {
        return new PreOrderTraversal.TreeIterator<Fragment>(TreeCursor.getCursor(startingRoot, FTree.treeAdapter()));
    }

    @Override
    public Fragment getRoot() {
        return root;
    }

    @Override
    public Iterator<Loss> lossIterator() {
        final Iterator<Fragment> fiter = fragments.iterator();
        fiter.next(); // ignore root
        return new Iterator<Loss>() {

            @Override
            public boolean hasNext() {
                return fiter.hasNext();
            }

            @Override
            public Loss next() {
                return fiter.next().getIncomingEdge();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public List<Loss> losses() {
        return new AbstractList<Loss>() {
            @Override
            public Loss get(int index) {
                return getFragmentAt(index + 1).getIncomingEdge();
            }

            @Override
            public int size() {
                return fragments.size() - 1;
            }
        };
    }

    @Override
    public int numberOfEdges() {
        return fragments.size()-1;
    }

    @Override
    public Loss getLoss(Fragment u, Fragment v) {
        final Loss l = v.getIncomingEdge();
        if (l.source == u) return l;
        else return null;
    }

    public final TreeCursor<Fragment> getCursor() {
        return TreeCursor.getCursor(root, FTree.treeAdapter());
    }

    public final TreeCursor getCursor(Fragment f) {
        if (fragments.get(f.vertexId) != f)
            throw new IllegalArgumentException("vertex " + f + " does not belong to this graph");
        return TreeCursor.getCursor(f, FTree.treeAdapter());
    }

    @Override
    public Iterator<Fragment> iterator() {
        return new Iterator<Fragment>() {
            int k = 0;

            @Override
            public boolean hasNext() {
                return k < fragments.size();
            }

            @Override
            public Fragment next() {
                if (!hasNext()) throw new NoSuchElementException();
                return fragments.get(k++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    protected Loss addLoss(Fragment u, Fragment v, MolecularFormula f) {
        if (v.inDegree > 0 && v.getIncomingEdge().source != u)
            throw new RuntimeException("Fragment " + v + " already have a parent.");
        final Loss loss = new Loss(u, v, f, 0d);
        if (u.outgoingEdges.length <= u.outDegree) {
            u.outgoingEdges = Arrays.copyOf(u.outgoingEdges, u.outDegree + 1);
        }
        u.outgoingEdges[u.outDegree] = loss;
        loss.sourceEdgeOffset = u.outDegree++;
        if (v.incomingEdges.length <= v.inDegree) {
            v.incomingEdges = Arrays.copyOf(v.incomingEdges, v.inDegree + 1);
        }
        v.incomingEdges[v.inDegree] = loss;
        loss.targetEdgeOffset = v.inDegree++;
        ++edgeNum;
        return loss;
    }

    public void normalizeStructure() {
        for (Fragment f : fragments) {
            final List<Loss> childs = new ArrayList<Loss>(f.getOutgoingEdges());
            Collections.sort(childs, new Comparator<Loss>() {
                @Override
                public int compare(Loss loss, Loss loss2) {
                    return loss.getTarget().getFormula().compareTo(loss2.getTarget().getFormula());
                }
            });
            int i = 0;
            for (Loss l : childs) {
                f.outgoingEdges[i] = l;
                l.sourceEdgeOffset = i;
                ++i;
            }
        }
        Collections.sort(fragments, new Comparator<Fragment>() {
            @Override
            public int compare(Fragment o1, Fragment o2) {
                return Double.compare(o2.getFormula().getMass(), o1.getFormula().getMass());
            }
        });
        for (int k=0; k < fragments.size(); ++k) {
            fragments.get(k).setVertexId(k);
        }

    }


}