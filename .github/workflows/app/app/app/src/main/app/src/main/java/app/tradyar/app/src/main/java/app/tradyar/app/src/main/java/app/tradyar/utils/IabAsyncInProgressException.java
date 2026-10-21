package app.tradyar.utils;
public class IabAsyncInProgressException extends Exception {
    public IabAsyncInProgressException() { super("Another async operation is in progress."); }
    public IabAsyncInProgressException(String message) { super(message); }
}
