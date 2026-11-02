package energytracker.energy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EiaRecord {
    public String period;
    public String fueltypeid;
    public String generation;

    public EiaRecord() {}

    public EiaRecord(String period, String fueltypeid, String generation) {
        this.period = period;
        this.fueltypeid = fueltypeid;
        this.generation = generation;
    }
}
