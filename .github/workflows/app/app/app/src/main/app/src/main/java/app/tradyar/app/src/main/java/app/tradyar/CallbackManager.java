package app.tradyar;
public class CallbackManager {
    private static CallbackManager instance;
    private CallbackManager() {}
    public static synchronized CallbackManager getInstance() {
        if (instance == null) instance = new CallbackManager();
        return instance;
    }
}
