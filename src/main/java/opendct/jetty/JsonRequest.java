package opendct.jetty;

public class JsonRequest {
    private String mangerName;
    private String requestName;
    private String parameters[];
    private String values[];

    public JsonRequest() {
        mangerName = "";
        requestName = "";
        parameters = new String[0];
        values = new String[0];
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }

    public String getMangerName() {
        return mangerName;
    }

    public void setMangerName(String mangerName) {
        this.mangerName = mangerName;
    }

    public String getRequestName() {
        return requestName;
    }

    public void setRequestName(String requestName) {
        this.requestName = requestName;
    }

    public String[] getParameters() {
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }
}
