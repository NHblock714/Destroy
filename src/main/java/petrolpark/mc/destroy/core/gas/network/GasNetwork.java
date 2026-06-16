package petrolpark.mc.destroy.core.gas.network;

import java.util.HashSet;
import java.util.Set;

import petrolpark.mc.destroy.core.gas.IGasVessel;
import petrolpark.mc.destroy.core.gas.valve.IGasValve;

/**
 * Pipe-network state for a graph of {@link IGasVessel}s connected via {@link IGasValve}s. Kept so
 * Atmosphere/IGasVessel/IGasValve/AndValve/OrValve form a complete (if partially stubbed)
 * package shape that downstream code can extend without re-creating these primitives.
*/
public class GasNetwork {

    public static int getMaxConnectedVessels() {
        return 16;
    }

    // protected final SimpleDirectedGraph<GasNetwork.Section, Holder<IGasValve>> network =
    // new SimpleDirectedGraph<Section, Holder<IGasValve>>(
    // (Class<? extends Holder<IGasValve>>) (new Holder<IGasValve>()).getClass());
    // protected final Map<GasNetwork.Section, Map<GasNetwork.Section, GraphPath<...>>> paths
    // = new HashMap<>();

    protected int connectedVesselsNo = 0;

    public void connect(GasNetwork.Section section1, GasNetwork.Section section2, IGasValve valve) {
        // When implementing:
        // if (network.addEdge(section1, section2, Holder.hold(valve))) { ... }
        // else { network.addEdge(section1, section2,
        // Holder.hold(OrValve.or(network.removeEdge(section1, section2).get(), valve))); }
    }

    public void tickAllConnections() {
    }

    public class Section {

        public final Set<IGasVessel> connectedVessels = new HashSet<>();

        /**
 * Attempt to connect another {@link IGasVessel} to this Section of the {@link GasNetwork}.
 * @return {@code true} only if the new Vessel was added (for the first time) and the
 * {@link #getMaxConnectedVessels} limit has not already been reached.
*/
        public boolean connect(IGasVessel vessel) {
            if (connectedVessels.size() >= getMaxConnectedVessels()) return false;
            boolean added = connectedVessels.add(vessel);
            if (added) {
                connectedVesselsNo++;
            }
            return added;
        }
    }
}
