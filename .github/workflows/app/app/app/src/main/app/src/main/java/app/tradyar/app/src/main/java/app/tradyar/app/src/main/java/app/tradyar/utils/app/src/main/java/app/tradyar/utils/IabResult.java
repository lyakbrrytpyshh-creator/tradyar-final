package app.tradyar.utils;
public class IabResult {
    private int mResponse;
    private String mMessage;
    public IabResult(int response, String message) {
        mResponse = response;
        if (message == null || message.trim().isEmpty()) mMessage = IabHelper.getResponseDesc(response);
        else mMessage = message;
    }
    public int getResponse() { return mResponse; }
    public String getMessage() { return mMessage; }
    public boolean isSuccess() { return mResponse == IabHelper.BILLING_RESPONSE_RESULT_OK; }
    public boolean isFailure() { return !isSuccess(); }
    @Override
    public String toString() { return "IabResult: " + mResponse + " - " + mMessage; }
}
