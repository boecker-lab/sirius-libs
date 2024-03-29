package de.unijena.bioinf.fragmenter;

import de.unijena.bioinf.ChemistryBase.chem.Smiles;
import org.junit.Test;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.AtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.*;

public class CombinatorialFragmenterTest {

    @Test
    public void testCutBond(){
        try {
            String smiles = "[H]C([H])([H])C(=O)O[H]";
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer aceticAcid = parser.parseSmiles(smiles);
            MolecularGraph mol = new MolecularGraph(aceticAcid);

            CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(mol);
            CombinatorialFragment[] fragments = fragmenter.cutBond(mol.asFragment(),3);

            BitSet frag1 = new BitSet(mol.natoms);
            frag1.set(0,4);
            BitSet frag2 = new BitSet(mol.natoms);
            frag2.set(4,8);

            assertTrue(frag1.equals(fragments[0].bitset) && frag2.equals(fragments[1].bitset));
        } catch(CDKException e){
            e.printStackTrace();
        }
    }

    @Test
    public void testCutRing(){
        try{
            String smiles = "c1(O)ccc(O)cc1";
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer quinol = parser.parseSmiles(smiles);
            MolecularGraph mol = new MolecularGraph(quinol);

            CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(mol);
            CombinatorialFragment[] fragments = fragmenter.cutRing(mol.asFragment(), 0,2,6);

            BitSet frag1 = new BitSet(mol.natoms);
            frag1.set(0,3);
            frag1.set(7);
            BitSet frag2 = (BitSet) frag1.clone();
            frag2.flip(0, mol.natoms);

            boolean containCorrectAtoms = frag1.equals(fragments[0].bitset) && frag2.equals(fragments[1].bitset);
            boolean noRingAnymore = fragments[0].disconnectedRings.cardinality() == 1 && fragments[1].disconnectedRings.cardinality() == 1;

            assertTrue(containCorrectAtoms && noRingAnymore);
        }catch(CDKException e){
            e.printStackTrace();
        }
    }

    @Test
    public void testCutAllBonds(){
        try{
            String smiles = "CC1CC1";
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer molecule = parser.parseSmiles(smiles);
            MolecularGraph mol = new MolecularGraph(molecule);

            CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(mol);
            List<CombinatorialFragment> fragments = fragmenter.cutAllBonds(mol.asFragment(), null);

            ArrayList<BitSet> bitsets = new ArrayList<>();

            // Schneiden der Bindung, die nicht zu einem Ring gehört
            BitSet frag = new BitSet(mol.natoms);
            frag.set(0);
            bitsets.add((BitSet) frag.clone());
            frag.flip(0,mol.natoms);
            bitsets.add((BitSet) frag.clone());

            // Schneiden der ersten und dritten Bindung im Ring:
            frag.clear();
            frag.set(0,2);
            bitsets.add((BitSet) frag.clone());
            frag.flip(0, mol.natoms);
            bitsets.add((BitSet) frag.clone());

            // Schneiden der ersten und zweiten Bindung im Ring:
            frag.clear();
            frag.set(0,2);
            frag.set(3);
            bitsets.add((BitSet) frag.clone());
            frag.flip(0, mol.natoms);
            bitsets.add((BitSet) frag.clone());

            // Schneiden der zweiten und dritten Bindung im Ring:
            frag.clear();
            frag.set(0,3);
            bitsets.add((BitSet) frag.clone());
            frag.flip(0, mol.natoms);
            bitsets.add((BitSet) frag.clone());

            assertEquals(bitsets.size(), fragments.size());

            for(CombinatorialFragment fragment : fragments){
                bitsets.remove(fragment.bitset);
            }

            assertTrue(bitsets.isEmpty());
        } catch (InvalidSmilesException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testEmptyMolecule(){
        IAtomContainer molecule = new AtomContainer();
        MolecularGraph mol = new MolecularGraph(molecule);
        CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(mol);
        CombinatorialGraph fragGraph = fragmenter.createCombinatorialFragmentationGraph(n -> true);
        assertTrue(fragGraph.getNodes().isEmpty()); //fragGraph.nodes is the set of all nodes without the root
    }

    @Test
    public void testCreateCombinatorialGraphWithoutFragmentationConstraint(){
        try{
            String smiles = "C1CC1";
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            MolecularGraph mol = new MolecularGraph(parser.parseSmiles(smiles));
            CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(mol);
            CombinatorialGraph graph = fragmenter.createCombinatorialFragmentationGraph(n -> true);

            assertEquals(7,graph.numberOfNodes());

            // Convert the sorted node list into an integer array - each bitset is seen as a binary number:
            ArrayList<CombinatorialNode> sortedNodeList = graph.getSortedNodeList();
            int[] numList = sortedNodeList.stream().mapToInt(n -> {
                int num = 0;
                for(int i = 0; i < mol.natoms; i++){
                    num = num + (n.fragment.bitset.get(i) ? (int) Math.pow(2,i) : 0);
                }
                return num;
            }).toArray();
            int[] bondbreaks = sortedNodeList.stream().mapToInt(n -> n.bondbreaks).toArray();
            int[] depths = sortedNodeList.stream().mapToInt(n -> n.depth).toArray();
            double[] totalScores = sortedNodeList.stream().mapToDouble(n -> n.totalScore).toArray();
            double[] scores = sortedNodeList.stream().mapToDouble(n -> n.score).toArray();

            assertArrayEquals(new int[]{1,2,3,4,5,6,7}, numList);
            assertArrayEquals(new int[]{1,1,1,1,1,1,0}, depths);
            assertArrayEquals(new int[]{2,2,2,2,2,2,0}, bondbreaks);
            assertArrayEquals(new double[]{-2.0,-2.0,-2.0,-2.0,-2.0,-2.0,0.0}, totalScores, 0.0);
            assertArrayEquals(new double[]{-2.0,-2.0,-2.0,-2.0,-2.0,-2.0,0.0}, scores, 0.0);

            // Compare the adjacency matrix for topology and edge+fragment scores:
            double[][] adjMatrix = graph.getAdjacencyMatrix();

            double minusInf = Double.NEGATIVE_INFINITY;
            double[][] expectedAdjMatrix = new double[][]{
                    {minusInf, minusInf, minusInf, minusInf, minusInf,minusInf,minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf,minusInf,minusInf},
                    {-1, -1, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf,minusInf,minusInf},
                    {-1, minusInf, minusInf, -1, minusInf,minusInf,minusInf},
                    {minusInf, -1, minusInf, -1, minusInf,minusInf,minusInf},
                    {-2,-2,-2,-2,-2,-2, minusInf}};

            for(int i = 0; i < graph.numberOfNodes(); i++){
                assertArrayEquals(expectedAdjMatrix[i], adjMatrix[i], 0.0);
            }

        } catch (InvalidSmilesException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateCombinatorialGraphWithFragmentationConstraint(){
        try{
            String smiles = "C1CC1";
            SmilesParser smilesParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            MolecularGraph molecule = new MolecularGraph(smilesParser.parseSmiles(smiles));

            CombinatorialFragmenter fragmenter = new CombinatorialFragmenter(molecule);
            CombinatorialGraph graph = fragmenter.createCombinatorialFragmentationGraph(n -> n.fragment.bitset.cardinality() > 2);

            // Convert the sorted node list into an integer array - each bitset is seen as a binary number:
            ArrayList<CombinatorialNode> sortedNodeList = graph.getSortedNodeList();
            int[] numList = sortedNodeList.stream().mapToInt(n -> {
                int num = 0;
                for(int i = 0; i < molecule.natoms; i++){
                    num = num + (n.fragment.bitset.get(i) ? (int) Math.pow(2,i) : 0);
                }
                return num;
            }).toArray();
            int[] bondbreaks = sortedNodeList.stream().mapToInt(n -> n.bondbreaks).toArray();
            int[] depths = sortedNodeList.stream().mapToInt(n -> n.depth).toArray();
            double[] totalScores = sortedNodeList.stream().mapToDouble(n -> n.totalScore).toArray();
            double[] scores = sortedNodeList.stream().mapToDouble(n -> n.score).toArray();

            assertArrayEquals(new int[]{1,2,3,4,5,6,7}, numList);
            assertArrayEquals(new int[]{1,1,1,1,1,1,0}, depths);
            assertArrayEquals(new int[]{2,2,2,2,2,2,0}, bondbreaks);
            assertArrayEquals(new double[]{-2.0,-2.0,-2.0,-2.0,-2.0,-2.0,0.0}, totalScores, 0.0);
            assertArrayEquals(new double[]{-2.0,-2.0,-2.0,-2.0,-2.0,-2.0,0.0}, scores, 0.0);

            // Check the adjacency matrix:
            double minusInf = Double.NEGATIVE_INFINITY;
            double[][] adjMatrix = graph.getAdjacencyMatrix();
            double[][] expAdjMatrix = new double[][]{
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {minusInf, minusInf, minusInf, minusInf, minusInf, minusInf, minusInf},
                    {-2,-2,-2,-2,-2,-2, minusInf}
            };

            for(int i = 0; i < graph.numberOfNodes(); i++){
                assertArrayEquals(expAdjMatrix[i], adjMatrix[i], 0.0);
            }

        } catch (InvalidSmilesException e) {
            e.printStackTrace();
        }

    }

}
