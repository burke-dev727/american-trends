package energytracker.energy;

import java.util.List;

/** Monthly energy mixes for periods between start and end, inclusive, sorted by period. */
public interface EnergySource {

    List<EnergyMix> energyMixes(String start, String end);
}
