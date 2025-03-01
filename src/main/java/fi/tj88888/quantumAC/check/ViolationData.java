package fi.tj88888.quantumAC.check;

/**
 * Class to carry violation information from check components back to main check classes.
 * Provides details about a detected violation and its severity.
 */
public class ViolationData {
    
    private final String details;
    private final int violationLevel;
    
    /**
     * Creates a new violation data object.
     * 
     * @param details A string containing details about the violation
     * @param violationLevel The violation level (severity)
     */
    public ViolationData(String details, int violationLevel) {
        this.details = details;
        this.violationLevel = violationLevel;
    }
    
    /**
     * Get the details of the violation.
     * 
     * @return A string containing details about the violation
     */
    public String getDetails() {
        return details;
    }
    
    /**
     * Get the violation level.
     * 
     * @return The violation level (severity)
     */
    public int getViolationLevel() {
        return violationLevel;
    }
    
    @Override
    public String toString() {
        return String.format("Violation[level=%d, details=%s]", violationLevel, details);
    }
} 