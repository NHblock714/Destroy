package petrolpark.mc.destroy.chemistry.legacy;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.Vec3;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException.FormulaException.FormulaModificationException;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException.FormulaException.FormulaRenderingException;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException.FormulaSerializationException;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException.MoleculeDeserializationException;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException.TopologyDefinitionException;
import petrolpark.mc.destroy.chemistry.legacy.LegacyBond.BondType;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure.Topology.SideChainInformation;
import petrolpark.mc.destroy.chemistry.serializer.Branch;
import petrolpark.mc.destroy.chemistry.serializer.Node;

/**
 * A Formula is all the {@link LegacyAtom Atoms} in a {@link LegacySpecies}, and the
 * {@link LegacyBond Bonds} that those Atoms have to other Atoms — a Molecule's 'structure'. The
 * {@link LegacyFunctionalGroup functional Groups} present in the Molecule are stored alongside.
 * Formulae are also referred to as "structures" throughout the Destroy JavaDocs.
 *
 * <p><b>Formulae must always be {@link #shallowCopy copied} before modifying.</b></p>
*/
public class LegacyMolecularStructure implements Cloneable {

    private Map<LegacyAtom, List<LegacyBond>> structure;
    private LegacyAtom startingAtom;
    private LegacyAtom currentAtom;
    private List<LegacyFunctionalGroup<?>> groups;
    private Topology topology;
    private List<Pair<SideChainInformation, LegacyMolecularStructure>> sideChains;

    @Nullable
    private String optimumFROWNSCode;

    private LegacyMolecularStructure() {
        structure = new LinkedHashMap<>();
        groups = new ArrayList<>();
        topology = Topology.LINEAR;
        sideChains = new ArrayList<>();
        optimumFROWNSCode = null;
    }

    public LegacyMolecularStructure(LegacyAtom startingAtom) {
        this();
        structure.put(startingAtom, new ArrayList<>());
        this.startingAtom = startingAtom;
        currentAtom = startingAtom;
    }

    private static LegacyMolecularStructure nothing() {
        return new LegacyMolecularStructure();
    }

    public LegacyMolecularStructure moveTo(LegacyAtom atom) {
        if (structure.containsKey(atom)) {
            currentAtom = atom;
        } else {
            throw new FormulaModificationException(this, "Can't set the current Atom to an Atom not in the Formula");
        }
        return this;
    }

    public LegacyMolecularStructure setStartingAtom(LegacyAtom atom) {
        if (structure.containsKey(atom)) {
            startingAtom = atom;
        } else {
            throw new FormulaModificationException(this, "Can't set the starting Atom to an Atom not in the Formula");
        }
        return this;
    }

    public static LegacyMolecularStructure atom(LegacyElement element) {
        return atom(element, 0);
    }

    public static LegacyMolecularStructure atom(LegacyElement element, double charge) {
        return new LegacyMolecularStructure(new LegacyAtom(element, charge));
    }

    public static LegacyMolecularStructure carbonChain(int length) {
        LegacyMolecularStructure carbonChain = atom(LegacyElement.CARBON);
        for (int i = 0; i < length - 1; i++) {
            carbonChain.addGroup(atom(LegacyElement.CARBON));
        }
        return carbonChain;
    }

    public static LegacyMolecularStructure alcohol() {
        return atom(LegacyElement.OXYGEN).addAtom(LegacyElement.HYDROGEN);
    }

    public static LegacyMolecularStructure borane() {
        return atom(LegacyElement.BORON).addAtom(LegacyElement.HYDROGEN).addAtom(LegacyElement.HYDROGEN);
    }

    public LegacyMolecularStructure addAtom(LegacyElement element) {
        return addAtom(element, BondType.SINGLE);
    }

    public LegacyMolecularStructure addAtom(LegacyElement element, BondType bondType) {
        addAtomToStructure(structure, currentAtom, new LegacyAtom(element), bondType);
        return this;
    }

    public LegacyMolecularStructure addAtom(LegacyAtom atom) {
        return addAtom(atom, BondType.SINGLE);
    }

    public LegacyMolecularStructure addAtom(LegacyAtom atom, BondType bondType) {
        addAtomToStructure(structure, currentAtom, atom, bondType);
        return this;
    }

    public boolean containsAtom(LegacyAtom atom) {
        return structure.containsKey(atom);
    }

    public LegacyMolecularStructure addGroup(LegacyMolecularStructure group) {
        return addGroup(group, true);
    }

    public LegacyMolecularStructure addGroup(LegacyMolecularStructure group, boolean isSideGroup) {
        return addGroup(group, isSideGroup, BondType.SINGLE);
    }

    public static LegacyMolecularStructure joinFormulae(LegacyMolecularStructure formula1,
                                                       LegacyMolecularStructure formula2,
                                                       BondType bondType) {
        LegacyMolecularStructure formula;
        if (formula2.isCyclic()) {
            if (formula1.isCyclic())
                throw new FormulaModificationException(formula1, "Cannot join two cyclic structures.");
            formula1.startingAtom = formula1.currentAtom;
            formula2.addGroup(formula1, false, bondType);
            formula = formula2;
        } else {
            formula2.startingAtom = formula2.currentAtom;
            formula1.addGroup(formula2, true, bondType);
            formula = formula1;
        }
        return formula.shallowCopy();
    }

    public LegacyMolecularStructure addGroup(LegacyMolecularStructure group, Boolean isSideGroup, BondType bondType) {
        if (topology.atomsAndLocations.stream().anyMatch(pair -> pair.getSecond() == currentAtom)) {
            throw new FormulaModificationException(this, "Cannot modify Atoms in cycle");
        }
        addGroupToStructure(structure, currentAtom, group, bondType);
        if (!isSideGroup) currentAtom = group.currentAtom;
        return this;
    }

    @Deprecated
    public LegacyMolecularStructure addGroupToPosition(LegacyMolecularStructure group, int position) {
        return addGroupToPosition(group, position, BondType.SINGLE);
    }

    @Deprecated
    public LegacyMolecularStructure addGroupToPosition(LegacyMolecularStructure group, int position, BondType bondType) {
        addGroupToStructure(structure, sideChains.get(position).getFirst().atom(), group, bondType);
        sideChains.get(position).setSecond(group);
        currentAtom = group.currentAtom;
        return this;
    }

    public boolean isCyclic() {
        return topology != Topology.LINEAR;
    }

    public List<Pair<Vec3, LegacyAtom>> getCyclicAtomsForRendering() {
        return topology.atomsAndLocations;
    }

    public List<LegacyBond> getCyclicBondsForRendering() {
        return topology.bonds;
    }

    public List<Pair<SideChainInformation, Branch>> getSideChainsForRendering() {
        return sideChains.stream().map(pair -> {
            LegacyMolecularStructure sideChain = pair.getSecond();
            return Pair.of(pair.getFirst(), getMaximumBranch(sideChain.startingAtom, sideChain.structure));
        }).toList();
    }

    public LegacyMolecularStructure remove(LegacyAtom atom) {
        if (!structure.containsKey(atom)) {
            throw new FormulaModificationException(this,
                "Cannot remove " + atom.getElement().getSymbol() + " atom (does not exist).");
        }
        if (atom == currentAtom) {
            throw new FormulaModificationException(this,
                "Cannot remove the currently selected Atom from a structure being built.");
        }
        if (topology.atomsAndLocations.stream().anyMatch(pair -> pair.getSecond() == atom)) {
            throw new FormulaModificationException(this,
                "Cannot remove Atoms in the cyclic part of a cyclic Molecule ");
        }

        for (LegacyBond bondToOtherAtom : structure.get(atom)) {
            structure.get(bondToOtherAtom.getDestinationAtom()).removeIf(bondToThisAtom ->
                bondToThisAtom.getDestinationAtom() == atom);
        }
        structure.remove(atom);
        return this;
    }

    public LegacyMolecularStructure replace(LegacyAtom oldAtom, LegacyAtom newAtom) {
        if (!structure.containsKey(oldAtom)) {
            throw new FormulaModificationException(this,
                "Cannot replace " + oldAtom.getElement().getSymbol() + " atom (does not exist).");
        }
        if (oldAtom == currentAtom) currentAtom = newAtom;
        if (oldAtom == startingAtom) startingAtom = newAtom;
        if (topology.atomsAndLocations.stream().anyMatch(pair -> pair.getSecond() == oldAtom)) {
            throw new FormulaModificationException(this,
                "Cannot replace Atoms in the cyclic part of a cyclic Molecule ");
        }

        for (LegacyBond bondToOtherAtom : structure.get(oldAtom)) {
            structure.get(bondToOtherAtom.getDestinationAtom()).replaceAll(bond -> {
                if (bond.getDestinationAtom() == oldAtom) {
                    return new LegacyBond(bond.getSourceAtom(), newAtom, bond.getType());
                }
                return bond;
            });
        }

        List<LegacyBond> oldBonds = structure.get(oldAtom);
        structure.put(newAtom, oldBonds);
        structure.remove(oldAtom);

        return this;
    }

    public LegacyMolecularStructure replaceBondTo(LegacyAtom otherAtom, BondType bondType) {
        for (LegacyBond bond : structure.get(currentAtom)) {
            if (bond.getDestinationAtom() == otherAtom) {
                bond.setType(bondType);
                for (LegacyBond reverseBond : structure.get(otherAtom)) {
                    if (reverseBond.getDestinationAtom() == currentAtom) {
                        reverseBond.setType(bondType);
                        refreshFunctionalGroups();
                        return this;
                    }
                }
            }
        }
        throw new FormulaModificationException(this,
            "Cannot modify bond between two Atoms if they do not already have a Bond");
    }

    public LegacyMolecularStructure insertBridgingAtom(LegacyAtom atom1, LegacyAtom atom2, LegacyAtom bridgeAtom) {
        if (!structure.containsKey(atom1) || !structure.containsKey(atom2))
            throw new FormulaModificationException(this, "Cannot add a bridging Atom between two Atoms not in this structure.");
        if (structure.containsKey(bridgeAtom))
            throw new FormulaModificationException(this, "Bridging Atom cannot already exist in the Molecular Structure.");
        LegacyBond bridgeBond = null;
        for (LegacyBond bond : structure.get(atom1))
            if (bond.getDestinationAtom() == atom2) { bridgeBond = bond; break; }
        if (bridgeBond == null)
            throw new FormulaModificationException(this, "Atoms to Bridge must be connected");
        boolean firstAtomCyclic = false, secondAtomCyclic = false;
        for (Pair<Vec3, LegacyAtom> cyclicAtom : topology.atomsAndLocations) {
            firstAtomCyclic |= cyclicAtom.getSecond() == atom1;
            secondAtomCyclic |= cyclicAtom.getSecond() == atom2;
            if (firstAtomCyclic && secondAtomCyclic)
                throw new FormulaModificationException(this,
                    "Cannot add Bridging Atom between two Atoms part of the cycle in a cyclic Molecular Structure");
        }

        structure.get(atom1).remove(bridgeBond);
        structure.get(atom2).removeIf(b -> b.getDestinationAtom() == atom1);
        addAtomToStructure(structure, atom1, bridgeAtom, bridgeBond.getType());
        addBondBetweenAtoms(structure, atom2, bridgeAtom, bridgeBond.getType());

        updateSideChainStructures();
        return this;
    }

    public LegacyMolecularStructure cleaveBondTo(LegacyAtom otherAtom) {
        LegacyBond bondCleaved = structure.get(currentAtom).stream()
            .filter(b -> b.getDestinationAtom() == otherAtom).findFirst()
            .orElseThrow(() -> new FormulaModificationException(this, "Cannot cleave Bond between two unconnected Atoms."));
        structure.get(currentAtom).remove(bondCleaved);
        structure.get(otherAtom).removeIf(b -> b.getDestinationAtom() == currentAtom);

        Set<LegacyAtom> visited = new HashSet<>(structure.size());
        Set<LegacyAtom> visitedTwice = new HashSet<>(structure.size());
        List<LegacyAtom> toVisit = new ArrayList<>(structure.size());
        boolean cycleInFirstFragment = false;
        toVisit.add(currentAtom);
        while (!toVisit.isEmpty()) {
            LegacyAtom atom = toVisit.get(0);
            toVisit.remove(atom);
            visited.add(atom);
            for (LegacyBond bond : structure.get(atom)) {
                LegacyAtom connectedAtom = bond.getDestinationAtom();
                if (visited.contains(connectedAtom)) {
                    visitedTwice.add(connectedAtom);
                    continue;
                }
                if (visitedTwice.contains(connectedAtom) || toVisit.contains(connectedAtom)) {
                    cycleInFirstFragment = true;
                } else {
                    toVisit.add(connectedAtom);
                }
            }
        }

        if (visited.contains(otherAtom)) {
            if (!cycleInFirstFragment) {
                topology = Topology.LINEAR;
                sideChains = Collections.emptyList();
                return this;
            } else {
                throw new FormulaModificationException(this, "Can only break bonds in simple rings, not polycyclic compounds.");
            }
        } else {
            Set<LegacyAtom> firstFragmentAtoms = new HashSet<>(visited);

            visited.clear();
            visitedTwice.clear();
            toVisit.clear();
            boolean cycleInSecondFragment = false;
            toVisit.add(otherAtom);
            while (!toVisit.isEmpty()) {
                LegacyAtom atom = toVisit.get(0);
                toVisit.remove(atom);
                visited.add(atom);
                for (LegacyBond bond : structure.get(atom)) {
                    LegacyAtom connectedAtom = bond.getDestinationAtom();
                    if (visited.contains(connectedAtom)) {
                        visitedTwice.add(connectedAtom);
                        continue;
                    }
                    if (visitedTwice.contains(connectedAtom) || toVisit.contains(connectedAtom)) {
                        cycleInSecondFragment = true;
                    } else {
                        toVisit.add(connectedAtom);
                    }
                }
            }

            LegacyMolecularStructure fragment;

            if (cycleInSecondFragment) {
                if (cycleInFirstFragment) {
                    throw new FormulaModificationException(this, "Cannot cleave bonds connecting two or more rings.");
                } else {
                    fragment = shallowCopy().removeAllWithoutChecks(visited);
                    removeAllWithoutChecks(firstFragmentAtoms);
                    fragment.startingAtom = fragment.currentAtom = currentAtom;
                    startingAtom = currentAtom = otherAtom;
                }
            } else {
                fragment = shallowCopy().removeAllWithoutChecks(firstFragmentAtoms);
                removeAllWithoutChecks(visited);
                fragment.startingAtom = fragment.currentAtom = otherAtom;
            }

            fragment.topology = Topology.LINEAR;
            fragment.sideChains.clear();

            return fragment;
        }
    }

    private LegacyMolecularStructure removeAllWithoutChecks(Collection<LegacyAtom> toRemove) {
        for (LegacyAtom atom : toRemove) structure.remove(atom);
        optimumFROWNSCode = null;
        return this;
    }

    public LegacyMolecularStructure addCarbonyl() {
        addAtom(LegacyElement.OXYGEN, BondType.DOUBLE);
        return this;
    }

    public LegacyMolecularStructure addAllHydrogens() {
        Map<LegacyAtom, List<LegacyBond>> newStructure = new LinkedHashMap<>(structure);

        if (topology != Topology.LINEAR) {
            for (int i = 0; i < sideChains.size(); i++) {
                LegacyAtom atom = sideChains.get(i).getFirst().atom();
                double totalBonds = getTotalBonds(newStructure.get(atom));
                if (atom.getElement().getNextLowestValency(totalBonds) - totalBonds > 0) {
                    LegacyAtom hydrogen = new LegacyAtom(LegacyElement.HYDROGEN);
                    sideChains.get(i).setSecond(new LegacyMolecularStructure(hydrogen));
                    addAtomToStructure(newStructure, atom, hydrogen, BondType.SINGLE);
                }
            }
        }

        for (Entry<LegacyAtom, List<LegacyBond>> entry : structure.entrySet()) {
            LegacyAtom atom = entry.getKey();
            List<LegacyBond> bonds = entry.getValue();
            double totalBonds = getTotalBonds(bonds);

            for (int i = 0;
                 i < atom.getElement().getNextLowestValency(totalBonds) - Math.abs(atom.formalCharge) - totalBonds;
                 i++) {
                LegacyAtom hydrogen = new LegacyAtom(LegacyElement.HYDROGEN);
                addAtomToStructure(newStructure, atom, hydrogen, BondType.SINGLE);
            }
        }
        structure = newStructure;
        return this;
    }

    public void updateSideChainStructures() {
        if (topology == Topology.LINEAR) return;

        for (Pair<SideChainInformation, LegacyMolecularStructure> sideChain : sideChains) {
            LegacyMolecularStructure sideChainFormula = sideChain.getSecond();
            if (sideChainFormula.startingAtom == null) continue;

            LegacyMolecularStructure newSideChainFormula = new LegacyMolecularStructure(sideChainFormula.startingAtom);
            Deque<LegacyAtom> frontier = new ArrayDeque<>();

            List<LegacyBond> startingBonds = new ArrayList<>();
            for (LegacyBond bond : structure.get(sideChainFormula.startingAtom)) {
                LegacyAtom potentialNewAtom = bond.getDestinationAtom();
                if (topology.formula.structure.containsKey(potentialNewAtom)) continue;
                if (!newSideChainFormula.structure.containsKey(potentialNewAtom)) {
                    newSideChainFormula.structure.put(potentialNewAtom, structure.get(potentialNewAtom));
                    frontier.push(potentialNewAtom);
                }
                startingBonds.add(bond);
            }
            newSideChainFormula.structure.put(sideChainFormula.startingAtom, startingBonds);

            while (!frontier.isEmpty()) {
                LegacyAtom visitAtom = frontier.pop();
                for (LegacyBond bond : structure.get(visitAtom)) {
                    LegacyAtom newAtom = bond.getDestinationAtom();
                    if (!newSideChainFormula.structure.containsKey(newAtom)) {
                        newSideChainFormula.structure.put(newAtom, structure.get(newAtom));
                        frontier.push(newAtom);
                    }
                }
            }

            sideChain.setSecond(newSideChainFormula);
        }
    }

    public Set<LegacyAtom> getAllAtoms() {
        return structure.keySet();
    }

    public List<LegacyAtom> getBondedAtomsOfElement(LegacyElement element) {
        return GroupFinder.bondedAtomsOfElementTo(structure, currentAtom, element);
    }

    public double getTotalBonds(List<LegacyBond> bonds) {
        float total = 0;
        for (LegacyBond bond : bonds) total += bond.getType().getEquivalent();
        return total;
    }

    public List<LegacyFunctionalGroup<?>> getFunctionalGroups() {
        return groups;
    }

    private Branch getStrippedBranchStartingWithAtom(LegacyAtom atom) {
        Map<LegacyAtom, List<LegacyBond>> newStructure = stripHydrogens(structure);
        if (topology == Topology.LINEAR) {
            return getMaximumBranch(atom, newStructure);
        }
        throw new FormulaSerializationException("Cannot serialize branch if it is cyclic.");
    }

    public String serialize() {
        if (optimumFROWNSCode != null) return optimumFROWNSCode;

        String body = "";
        String prefix = topology.getID();

        if (topology == Topology.LINEAR) {
            Map<LegacyAtom, List<LegacyBond>> newStructure = stripHydrogens(structure);
            Branch maxBranch = getMaximumBranchWithHighestMass(newStructure);
            body = maxBranch == null ? "" : maxBranch.serialize();
        } else {
            updateSideChainStructures();
            List<Branch> identity = new ArrayList<>(topology.getConnections());

            if (topology.getConnections() > 0) for (int i = 0; i < topology.getConnections(); i++) {
                LegacyMolecularStructure sideChain = sideChains.get(i).getSecond();
                if (sideChain.getAllAtoms().isEmpty() || sideChain.startingAtom.isNeutralHydrogen()) {
                    identity.add(new Branch(new Node(new LegacyAtom(LegacyElement.HYDROGEN))));
                } else {
                    identity.add(sideChain.getStrippedBranchStartingWithAtom(sideChain.startingAtom));
                }
            }

            List<List<Branch>> possibleReflections = new ArrayList<>(topology.getReflections().length + 1);
            possibleReflections.add(identity);

            for (int[] reflectionOrder : topology.getReflections()) {
                List<Branch> reflection = new ArrayList<>(topology.getConnections());
                for (int reflectedBranchPosition : reflectionOrder) {
                    reflection.add(identity.get(reflectedBranchPosition));
                }
                possibleReflections.add(reflection);
            }

            possibleReflections.sort((r1, r2) -> getReflectionComparison(r1).compareTo(getReflectionComparison(r2)));

            List<Branch> bestReflection = possibleReflections.get(0);
            StringBuilder sb = new StringBuilder();
            if (!bestReflection.isEmpty()) for (int i = 0; i < topology.getConnections(); i++) {
                Branch branch = bestReflection.get(i);
                if (!branch.getStartNode().getAtom().isNeutralHydrogen()) {
                    sb.append(branch.serialize());
                }
                sb.append(",");
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 1);
            body = sb.toString();
        }

        optimumFROWNSCode = prefix + ":" + body;
        return optimumFROWNSCode;
    }

    private static Float getReflectionComparison(List<Branch> reflection) {
        float total = 0f;
        for (int i = 0; i < reflection.size(); i++) {
            total += i * reflection.get(i).getMassOfLongestChain();
        }
        return total;
    }

    private static Branch getMaximumBranchWithHighestMass(Map<LegacyAtom, List<LegacyBond>> structure) {
        List<LegacyAtom> terminalAtoms = new ArrayList<>();
        for (LegacyAtom atom : structure.keySet()) {
            if (structure.get(atom).size() == 1) terminalAtoms.add(atom);
        }

        if (terminalAtoms.isEmpty()) {
            // No chain end (a bare ring, or a structure with no terminal atom) — anchor on any atom so
            // the caller does not index an empty list.
            if (structure.isEmpty()) return null;
            return getMaximumBranch(structure.keySet().iterator().next(), structure);
        }

        terminalAtoms.sort((a1, a2) ->
            getMaximumBranch(a2, structure).getMassOfLongestChain()
                .compareTo(getMaximumBranch(a1, structure).getMassOfLongestChain()));

        terminalAtoms.sort((a1, a2) ->
            Branch.getMassForComparisonInSerialization(a1).compareTo(Branch.getMassForComparisonInSerialization(a2)));

        return getMaximumBranch(terminalAtoms.get(0), structure);
    }

    public static LegacyMolecularStructure deserialize(String FROWNSstring) {
        try {
            LegacyMolecularStructure formula;

            String[] topologyAndFormula = FROWNSstring.strip().split(":");
            Topology topology;
            String formulaString;

            if (topologyAndFormula.length == 3) {
                topology = Topology.getTopology(topologyAndFormula[0] + ":" + topologyAndFormula[1]);
                formulaString = topologyAndFormula[2];
            } else {
                throw new MoleculeDeserializationException("Badly formatted FROWNS string '" + FROWNSstring
                    + "'. They should be in the format 'namespace:topology:chains'.");
            }

            if (topology == Topology.LINEAR) {
                List<String> symbols = Arrays.stream(formulaString.split("(?=\\p{Upper})")).toList();
                formula = groupFromString(symbols);
            } else {
                if (topology.formula == null)
                    throw new MoleculeDeserializationException("Missing base formula for Topology " + topology.getID());
                formula = topology.formula.shallowCopy();
                if (topology.getConnections() == 0) return formula.refreshFunctionalGroups();
                int i = 0;
                for (String group : formulaString.split(",", -1)) {
                    if (i > formula.topology.connections.size())
                        throw new MoleculeDeserializationException("Formula '" + FROWNSstring + "' has too many groups for its Topology. There should be "
                            + formula.topology.connections.size() + ".");
                    LegacyMolecularStructure sideChain;
                    if (group.isBlank()) {
                        sideChain = new LegacyMolecularStructure(new LegacyAtom(LegacyElement.HYDROGEN));
                    } else {
                        sideChain = groupFromString(Arrays.stream(group.split("(?=\\p{Upper})")).toList());
                    }
                    formula.addGroupToPosition(sideChain, i, formula.topology.connections.get(i).bondType());
                    i++;
                }
            }

            formula.addAllHydrogens().refreshFunctionalGroups();
            formula.updateSideChainStructures();
            return formula;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Could not parse FROWNS String '" + FROWNSstring + "'", e);
        }
    }

    public LegacyMolecularStructure refreshFunctionalGroups() {
        groups = new ArrayList<>();
        for (GroupFinder finder : GroupFinder.allGroupFinders()) {
            groups.addAll(finder.findGroups(structure));
        }
        return this;
    }

    public LegacyMolecularStructure shallowCopy() {
        try {
            LegacyMolecularStructure newFormula = (LegacyMolecularStructure) super.clone();
            newFormula.structure = new LinkedHashMap<>(structure.size());
            newFormula.structure = shallowCopyStructure(structure);
            newFormula.groups = new ArrayList<>(groups);
            newFormula.topology = this.topology;
            updateSideChainStructures();
            newFormula.sideChains = sideChains.stream()
                .map(pair -> Pair.of(pair.getFirst(), pair.getSecond().shallowCopy()))
                .collect(Collectors.toList());
            newFormula.optimumFROWNSCode = null;
            return newFormula;
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    public Float getCarbocationStability(LegacyAtom carbon, boolean isCarbanion) {
        Float totalElectronegativity = 0f;
        for (LegacyBond bond : structure.get(carbon)) {
            totalElectronegativity += bond.getDestinationAtom().getElement().getElectronegativity()
                * bond.getType().getEquivalent();
        }
        Float relativeElectronegativity = totalElectronegativity - (LegacyElement.CARBON.getElectronegativity() * 4);
        Float relativeStability = 1f + ((float) Math.pow(relativeElectronegativity, 4) / (float) Math.abs(relativeElectronegativity));
        return isCarbanion ^ relativeElectronegativity < 0 ? 1f / relativeStability : relativeStability;
    }

    public Branch getRenderBranch() {
        if (topology != Topology.LINEAR)
            throw new FormulaRenderingException(this, "Cannot get a Render branch for a cyclic Molecule.");
        return getMaximumBranchWithHighestMass(structure);
    }

    // INTERNAL METHODS

    private static Map<LegacyAtom, List<LegacyBond>> shallowCopyStructure(Map<LegacyAtom, List<LegacyBond>> structureToCopy) {
        Map<LegacyAtom, List<LegacyBond>> newStructure = new LinkedHashMap<>();
        for (LegacyAtom atom : structureToCopy.keySet()) {
            List<LegacyBond> oldBonds = structureToCopy.get(atom);
            List<LegacyBond> newBonds = new ArrayList<>();
            for (LegacyBond oldBond : oldBonds) {
                newBonds.add(new LegacyBond(atom, oldBond.getDestinationAtom(), oldBond.getType()));
            }
            newStructure.put(atom, newBonds);
        }
        return newStructure;
    }

    private static Branch getMaximumBranch(LegacyAtom startAtom, Map<LegacyAtom, List<LegacyBond>> structure) {
        Map<LegacyAtom, Node> allNodes = new HashMap<>();
        for (LegacyAtom atom : structure.keySet()) allNodes.put(atom, new Node(atom));

        Node currentNode = allNodes.get(startAtom);
        currentNode.visited = true;

        Branch maximumBranch = new Branch(currentNode);

        Boolean nodesAdded = true;
        while (nodesAdded) {
            nodesAdded = false;
            Map<Node, BondType> connectedUnvisitedNodesAndTheirBondTypes = new HashMap<>();
            for (LegacyBond bond : structure.get(currentNode.getAtom())) {
                Node connectedNode = allNodes.get(bond.getDestinationAtom());
                if (connectedNode != null && !connectedNode.visited) {
                    connectedUnvisitedNodesAndTheirBondTypes.put(connectedNode, bond.getType());
                }
            }

            if (connectedUnvisitedNodesAndTheirBondTypes.size() == 1) {
                Node onlyNode = connectedUnvisitedNodesAndTheirBondTypes.keySet().iterator().next();
                maximumBranch.add(onlyNode, connectedUnvisitedNodesAndTheirBondTypes.get(onlyNode));
                currentNode = onlyNode;
                nodesAdded = true;
            } else if (!connectedUnvisitedNodesAndTheirBondTypes.isEmpty()) {
                Map<Branch, BondType> connectedBranchesAndTheirBondTypes = new HashMap<>();
                for (Node node : connectedUnvisitedNodesAndTheirBondTypes.keySet()) {
                    Map<LegacyAtom, List<LegacyBond>> newStructure = shallowCopyStructure(structure);
                    LegacyBond bondToRemove = null;
                    for (LegacyBond bond : structure.get(node.getAtom())) {
                        if (bond.getDestinationAtom() == currentNode.getAtom()) {
                            bondToRemove = bond;
                        }
                    }
                    if (bondToRemove != null) newStructure.get(node.getAtom()).remove(bondToRemove);
                    newStructure.remove(currentNode.getAtom());

                    Branch branch = getMaximumBranch(node.getAtom(), newStructure);
                    connectedBranchesAndTheirBondTypes.put(branch, connectedUnvisitedNodesAndTheirBondTypes.get(node));
                }

                List<Branch> orderedConnectedBranches = new ArrayList<>(connectedBranchesAndTheirBondTypes.keySet());
                orderedConnectedBranches.sort((b1, b2) -> b2.getMass().compareTo(b1.getMass()));

                Branch biggestBranch = orderedConnectedBranches.get(0);
                maximumBranch.add(biggestBranch, connectedBranchesAndTheirBondTypes.get(biggestBranch));

                orderedConnectedBranches.remove(0);
                for (Branch sideBranch : orderedConnectedBranches) {
                    currentNode.addSideBranch(sideBranch, connectedBranchesAndTheirBondTypes.get(sideBranch));
                }
            }
        }

        return maximumBranch;
    }

    private static void addAtomToStructure(Map<LegacyAtom, List<LegacyBond>> structureToMutate,
                                           LegacyAtom rootAtom, LegacyAtom newAtom, BondType bondType) {
        structureToMutate.put(newAtom, new ArrayList<>());
        addBondBetweenAtoms(structureToMutate, rootAtom, newAtom, bondType);
    }

    private static void addGroupToStructure(Map<LegacyAtom, List<LegacyBond>> structureToMutate,
                                            LegacyAtom rootAtom, LegacyMolecularStructure group, BondType bondType) {
        if (group.topology != Topology.LINEAR) {
            throw new FormulaModificationException(group,
                "Cannot add Cycles as side-groups - to create a Cyclic Molecule, start with the Cycle and use addGroupAtPosition(), or use Formula.joinFormulae if this is in a Generic Reaction");
        }
        for (Entry<LegacyAtom, List<LegacyBond>> entry : group.structure.entrySet()) {
            if (structureToMutate.containsKey(entry.getKey()))
                throw new FormulaModificationException(group, "Cannot add a derivative of a Formula to itself.");
            structureToMutate.put(entry.getKey(), entry.getValue());
        }
        addBondBetweenAtoms(structureToMutate, rootAtom, group.startingAtom, bondType);
    }

    private static void addBondBetweenAtoms(Map<LegacyAtom, List<LegacyBond>> structureToMutate,
                                            LegacyAtom atom1, LegacyAtom atom2, BondType type) {
        LegacyBond bond = new LegacyBond(atom1, atom2, type);
        structureToMutate.get(atom1).add(bond);
        structureToMutate.get(atom2).add(bond.getMirror());
    }

    private static LegacyMolecularStructure groupFromString(List<String> symbols) {
        LegacyMolecularStructure formula = nothing();
        Boolean hasFormulaBeenInstantiated = false;

        BondType nextAtomBond = BondType.SINGLE;

        int i = 0;
        while (i < symbols.size()) {
            if (symbols.get(i).matches(".*\\)"))
                throw new MoleculeDeserializationException(
                    "Chain bond type symbols must preceed side groups; for example chloroethene must be 'destroy:linear:C=(Cl)C' and not 'destroy:linear:C(Cl)=C'.");

            String symbol;
            Map<LegacyMolecularStructure, BondType> groupsToAdd = new HashMap<>();
            BondType thisAtomBond = nextAtomBond;

            if (symbols.get(i).contains("(")) {
                BondType groupBond = trailingBondType(symbols.get(i));
                symbol = symbols.get(i).substring(0, symbols.get(i).indexOf('('));

                int brackets = 1;
                List<String> subSymbols = new ArrayList<>();
                while (brackets > 0) {
                    i++;
                    Boolean added = false;
                    for (int j = 0; j < symbols.get(i).length(); j++) {
                        char c = symbols.get(i).charAt(j);
                        if (c == ')') brackets--;
                        else if (c == '(') brackets++;
                        if (brackets == 0) {
                            subSymbols.add(symbols.get(i).substring(0, j));
                            groupsToAdd.put(groupFromString(subSymbols), groupBond);
                            subSymbols = new ArrayList<>();
                            groupBond = trailingBondType(symbols.get(i));
                            added = true;
                        }
                    }
                    if (!added) subSymbols.add(symbols.get(i));
                }
            } else {
                symbol = symbols.get(i);
            }

            Boolean stripBond = true;
            nextAtomBond = BondType.SINGLE;
            switch (symbol.charAt(symbol.length() - 1)) {
                case '=' -> nextAtomBond = BondType.DOUBLE;
                case '#' -> nextAtomBond = BondType.TRIPLE;
                case '~' -> nextAtomBond = BondType.AROMATIC;
                default -> stripBond = false;
            }
            if (stripBond) symbol = symbol.substring(0, symbol.length() - 1);

            double charge = 0;
            String[] symbolAndCharge = symbol.split("\\^");
            if (symbolAndCharge.length != 1) {
                symbol = symbolAndCharge[0];
                charge = Double.parseDouble(symbolAndCharge[1]);
            }

            char lastChar = symbol.charAt(symbol.length() - 1);
            int rGroupNumber = 0;
            if (Character.isDigit(lastChar)) {
                symbol = symbol.substring(0, symbol.length() - 1);
                rGroupNumber = lastChar - '0';
            }
            LegacyAtom atom = new LegacyAtom(LegacyElement.fromSymbol(symbol), charge);
            atom.rGroupNumber = rGroupNumber;

            if (hasFormulaBeenInstantiated) {
                formula.addGroup(new LegacyMolecularStructure(atom), false, thisAtomBond);
            } else {
                formula = new LegacyMolecularStructure(atom);
                hasFormulaBeenInstantiated = true;
            }

            for (LegacyMolecularStructure group : groupsToAdd.keySet()) {
                formula.addGroup(group, true, groupsToAdd.get(group));
            }

            i++;
        }
        return formula;
    }

    private static BondType trailingBondType(String symbol) {
        return BondType.fromFROWNSCode(symbol.charAt(symbol.length() - 1));
    }

    private static Map<LegacyAtom, List<LegacyBond>> stripHydrogens(Map<LegacyAtom, List<LegacyBond>> structure) {
        Map<LegacyAtom, List<LegacyBond>> newStructure = new LinkedHashMap<>();
        for (Entry<LegacyAtom, List<LegacyBond>> entry : structure.entrySet()) {
            LegacyAtom atom = entry.getKey();
            List<LegacyBond> bondsToInclude = new ArrayList<>();
            boolean includeAtom = !atom.isNeutralHydrogen();
            for (LegacyBond bond : entry.getValue()) {
                if (atom.formalCharge != 0 || bond.getDestinationAtom().formalCharge != 0
                    || !bond.getDestinationAtom().isNeutralHydrogen()) {
                    bondsToInclude.add(bond);
                    if (bond.getDestinationAtom().formalCharge != 0) includeAtom = true;
                }
            }
            if (includeAtom) newStructure.put(atom, bondsToInclude);
        }
        return newStructure;
    }

    /**
 * A 3D structure of a {@link LegacySpecies} if it is cyclic.
*/
    public static class Topology {

        private static final Map<String, Topology> TOPOLOGIES = new HashMap<>();

        public static final Topology LINEAR = new Builder(Destroy.MOD_ID).build("linear");

        private final String nameSpace;
        private String id;

        private LegacyMolecularStructure formula;

        private final List<Pair<Vec3, LegacyAtom>> atomsAndLocations;
        private final List<LegacyBond> bonds;
        private final List<SideChainInformation> connections;

        private int[][] reflections = null;

        private Topology(String nameSpace) {
            this.nameSpace = nameSpace;
            formula = null;
            atomsAndLocations = new ArrayList<>();
            bonds = new ArrayList<>();
            connections = new ArrayList<>();
        }

        public static Topology getTopology(String id) {
            return TOPOLOGIES.get(id);
        }

        public String getID() {
            return nameSpace + ":" + id;
        }

        public int getConnections() {
            return connections.size();
        }

        public int[][] getReflections() {
            return reflections;
        }

        public static class Builder {

            private final String nameSpace;
            private final Topology topology;

            public Builder(String nameSpace) {
                this.nameSpace = nameSpace;
                topology = new Topology(nameSpace);
            }

            public Builder startWith(LegacyElement element) {
                return startWith(element, 0d);
            }

            public Builder startWith(LegacyElement element, double charge) {
                topology.formula = LegacyMolecularStructure.atom(element, charge);
                topology.atomsAndLocations.add(Pair.of(new Vec3(0f, 0f, 0f), topology.formula.startingAtom));
                return this;
            }

            public Builder sideChain(Vec3 bondDirection, Vec3 branchDirection) {
                return sideChain(bondDirection, branchDirection, BondType.SINGLE);
            }

            public Builder sideChain(Vec3 bondDirection, Vec3 branchDirection, BondType bondType) {
                if (topology.reflections != null)
                    throw new TopologyDefinitionException("Cannot add more side chains once the reflections have been declared.");
                topology.connections.add(new SideChainInformation(
                    topology.atomsAndLocations.get(0).getSecond(), bondDirection, branchDirection, bondType));
                return this;
            }

            public AttachedAtom atom(LegacyElement element, Vec3 location) {
                return atom(element, 0d, location);
            }

            public AttachedAtom atom(LegacyElement element, double charge, Vec3 location) {
                if (topology.formula == null)
                    throw new TopologyDefinitionException("Cannot add Atoms to a Topology that hasn't been initialized with startWith(Element)");
                LegacyAtom atom = new LegacyAtom(element, charge);
                topology.formula.structure.put(atom, new ArrayList<>());
                topology.atomsAndLocations.add(Pair.of(location, atom));
                return new AttachedAtom(this, atom);
            }

            public Topology.Builder reflections(int[][] reflections) {
                int connections = topology.getConnections();
                for (int[] reflection : reflections) {
                    int sum = 0;
                    for (int i : reflection) sum += Math.pow(2, i);
                    if (sum != (int) Math.pow(2, connections) - 1 || reflection.length != connections)
                        throw new TopologyDefinitionException("Isomer configurations must match the number of side chains this Topology has.");
                }
                topology.reflections = reflections;
                return this;
            }

            public Topology build(String id) {
                topology.id = id;
                if (topology.formula != null) {
                    topology.formula.topology = topology;
                    topology.connections.forEach(sideChainInfo ->
                        topology.formula.sideChains.add(Pair.of(sideChainInfo, new LegacyMolecularStructure())));
                }
                if (topology.reflections == null) topology.reflections = new int[topology.connections.size()][0];
                TOPOLOGIES.put(nameSpace + ":" + id, topology);
                return topology;
            }
        }

        public static class AttachedAtom {

            private final Builder builder;
            private final LegacyAtom atom;

            private AttachedAtom(Builder builder, LegacyAtom atom) {
                this.builder = builder;
                this.atom = atom;
            }

            public AttachedAtom withBondTo(int n, BondType bondType) {
                if (n >= builder.topology.atomsAndLocations.size())
                    throw new TopologyDefinitionException("Tried to Bond an Atom back to Atom " + n + " but the "
                        + n + "th atom has not yet been added to the Topology.");
                if (builder.topology.formula == null)
                    throw new TopologyDefinitionException("Cannot add Bonds between Atoms that do not exist on the structure.");
                LegacyAtom atomToWhichToAttach = builder.topology.atomsAndLocations.get(n).getSecond();
                builder.topology.bonds.add(new LegacyBond(atom, atomToWhichToAttach, bondType));
                addBondBetweenAtoms(builder.topology.formula.structure, atom, atomToWhichToAttach, bondType);
                return this;
            }

            public AttachedAtom withSideBranch(Vec3 bondDirection, Vec3 branchDirection) {
                return withSideBranch(bondDirection, branchDirection, BondType.SINGLE);
            }

            public AttachedAtom withSideBranch(Vec3 bondDirection, Vec3 branchDirection, BondType bondType) {
                if (builder.topology.reflections != null)
                    throw new TopologyDefinitionException("Cannot add more side chains once the reflections have been declared.");
                builder.topology.connections.add(new SideChainInformation(atom, bondDirection, branchDirection, bondType));
                return this;
            }

            public Builder attach() {
                return builder;
            }
        }

        public record SideChainInformation(LegacyAtom atom, Vec3 bondDirection,
                                           Vec3 branchDirection, BondType bondType) {}
    }
}
