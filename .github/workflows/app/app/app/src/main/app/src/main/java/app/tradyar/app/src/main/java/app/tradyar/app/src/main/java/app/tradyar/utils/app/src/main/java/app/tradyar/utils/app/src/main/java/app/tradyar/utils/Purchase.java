package app.tradyar.utils;
import org.json.JSONException;
import org.json.JSONObject;
public class Purchase {
    String mItemType, mOrderId, mPackageName, mSku, mDeveloperPayload, mToken, mOriginalJson, mSignature;
    long mPurchaseTime;
    int mPurchaseState;
    boolean mIsAutoRenewing;
    public Purchase(String itemType, String jsonPurchaseInfo, String signature) throws JSONException {
        mItemType = itemType;
        mOriginalJson = jsonPurchaseInfo;
        mSignature = signature;
        JSONObject o = new JSONObject(mOriginalJson);
        mOrderId = o.optString("orderId");
        mPackageName = o.optString("packageName");
        mSku = o.optString("productId");
        mPurchaseTime = o.optLong("purchaseTime");
        mPurchaseState = o.optInt("purchaseState");
        mDeveloperPayload = o.optString("developerPayload");
        mToken = o.optString("token", o.optString("purchaseToken"));
        mIsAutoRenewing = o.optBoolean("autoRenewing");
    }
    public String getItemType() { return mItemType; }
    public String getOrderId() { return mOrderId; }
    public String getPackageName() { return mPackageName; }
    public String getSku() { return mSku; }
    public long getPurchaseTime() { return mPurchaseTime; }
    public int getPurchaseState() { return mPurchaseState; }
    public String getDeveloperPayload() { return mDeveloperPayload; }
    public String getToken() { return mToken; }
    public String getOriginalJson() { return mOriginalJson; }
    public String getSignature() { return mSignature; }
    public boolean isAutoRenewing() { return mIsAutoRenewing; }
}
