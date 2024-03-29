package de.unijena.bioinf.fragmenter;

import java.util.ArrayList;

public class CombinatorialSubtreeManipulator {

    public static double removeDanglingSubtrees(CombinatorialSubtree tree){
        // On-the-fly removal of subtrees with negative profit:
        return getBestSubtreeScore(tree.getRoot(), tree);
    }

    private static double getBestSubtreeScore(CombinatorialNode currentNode, CombinatorialSubtree tree){
        double score = 0;
        ArrayList<CombinatorialEdge> outgoingEdges = new ArrayList<>(currentNode.getOutgoingEdges());

        for(CombinatorialEdge edge : outgoingEdges){
            CombinatorialNode child = edge.target;
            double childScore = getBestSubtreeScore(child, tree) + child.fragmentScore + edge.score;

            if(childScore < 0){ // retaining the subtree below 'child' results in a smaller profit of this whole tree
                tree.removeSubtree(child.fragment);
            }else{
                score = score + childScore;
            }
        }
        return score;
    }
}
