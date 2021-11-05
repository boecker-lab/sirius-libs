package de.unijena.bioinf.fragmenter;

import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.ft.Fragment;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class PCSTFragmentationTreeAnnotator extends AbstractFragmentationTreeAnnotator{

    private double score;

    public PCSTFragmentationTreeAnnotator(FTree fTree, MolecularGraph molecule, CombinatorialFragmenterScoring scoring){
        super(fTree, molecule, scoring);
    }

    @Override
    public HashMap<Fragment, List<CombinatorialFragment>> computeMapping() {
        return null;
    }
}
