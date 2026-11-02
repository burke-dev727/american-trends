package energytracker.energy;

/** A data source failed while serving a request. The API reports this as a bad gateway. */
public class EnergyDataException extends RuntimeException {

    public EnergyDataException(String message) {
        super(message);
    }

    public EnergyDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
