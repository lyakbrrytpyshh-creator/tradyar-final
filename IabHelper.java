package app.tradyar.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * IabHelper - In-App Billing Helper for Myket.
 * Based on the official Myket TrivialDrive sample.
 */
public class IabHelper {
    private static final String TAG = "IabHelper";

    // Billing response codes
    public static final int BILLING_RESPONSE_RESULT_OK = 0;
    public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    public static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    // IAB Helper error codes
    public static final int IABHELPER_ERROR_BASE = -1000;
    public static final int IABHELPER_REMOTE_EXCEPTION = -1001;
    public static final int IABHELPER_BAD_RESPONSE = -1002;
    public static final int IABHELPER_VERIFICATION_FAILED = -1003;
    public static final int IABHELPER_SEND_INTENT_FAILED = -1004;
    public static final int IABHELPER_USER_CANCELLED = -1005;
    public static final int IABHELPER_UNKNOWN_PURCHASE_RESPONSE = -1006;
    public static final int IABHELPER_MISSING_TOKEN = -1007;
    public static final int IABHELPER_UNKNOWN_ERROR = -1008;
    public static final int IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE = -1009;
    public static final int IABHELPER_INVALID_CONSUMPTION = -1010;
    public static final int IABHELPER_INVALID_PUBLIC_KEY = -1011;

    // Item types
    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    // Keys for responses
    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    private static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    private static final String RESPONSE_INAPP_PURCHASE_LIST = "INAPP_PURCHASE_LIST";
    private static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    private static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    private static final String RESPONSE_INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    private static final int API_VERSION = 3;

    private final Context mContext;
    private final String mSignatureBase64;

    private boolean mDebugLog = false;
    private boolean mSetupDone = false;
    private boolean mAsyncInProgress = false;
    private String mAsyncOperation = "";
    private boolean mSubscriptionsSupported = false;

    private IInAppBillingService mService;
    private ServiceConnection mServiceConn;

    private OnIabPurchaseFinishedListener mPurchaseListener;
    private String mPurchasingItemType;
    private int mRequestCode = 0;

    public IabHelper(Context ctx, String base64PublicKey) {
        mContext = ctx.getApplicationContext();
        mSignatureBase64 = base64PublicKey;
        logDebug("IAB helper created.");
    }

    public void enableDebugLogging(boolean enable) {
        mDebugLog = enable;
    }

    private void logDebug(String msg) {
        if (mDebugLog) Log.d(TAG, msg);
    }

    private void logError(String msg) {
        Log.e(TAG, "In-app billing error: " + msg);
    }

    private void logWarn(String msg) {
        Log.w(TAG, "In-app billing warning: " + msg);
    }

    public void setRequestCode(int requestCode) {
        mRequestCode = requestCode;
    }

    public void startSetup(final OnIabSetupFinishedListener listener) {
        if (mSetupDone) throw new IllegalStateException("IAB helper is already set up.");
        if (TextUtils.isEmpty(mSignatureBase64)) {
            throw new IllegalArgumentException("Public key is empty.");
        }

        logDebug("Starting in-app billing setup.");

        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                logDebug("Billing service disconnected.");
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                logDebug("Billing service connected.");
                mService = IInAppBillingService.Stub.asInterface(service);
                String packageName = mContext.getPackageName();
                try {
                    int response = mService.isBillingSupported(API_VERSION, packageName, ITEM_TYPE_INAPP);
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        if (listener != null) {
                            listener.onIabSetupFinished(new IabResult(response, "Error checking for billing v3 support."));
                        }
                        return;
                    }

                    response = mService.isBillingSupported(API_VERSION, packageName, ITEM_TYPE_SUBS);
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        logDebug("Subscriptions are available.");
                        mSubscriptionsSupported = true;
                    } else {
                        logDebug("Subscriptions are not available.");
                    }

                    mSetupDone = true;
                } catch (RemoteException e) {
                    if (listener != null) {
                        listener.onIabSetupFinished(new IabResult(IABHELPER_REMOTE_EXCEPTION, "RemoteException while checking for billing v3 support."));
                    }
                    return;
                }

                if (listener != null) {
                    listener.onIabSetupFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Setup successful."));
                }
            }
        };

        Intent serviceIntent = new Intent("ir.mservices.market.InAppBillingService.BIND");
        serviceIntent.setPackage("ir.mservices.market");
        List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
        if (services == null || services.isEmpty()) {
            logError("Myket billing service is unavailable.");
            if (listener != null) {
                listener.onIabSetupFinished(new IabResult(BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE, "Myket is not installed."));
            }
            return;
        }

        if (!mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE)) {
            logError("Could not bind to service.");
            if (listener != null) {
                listener.onIabSetupFinished(new IabResult(IABHELPER_REMOTE_EXCEPTION, "Could not bind to service."));
            }
        }
    }

    public void dispose() {
        logDebug("Disposing.");
        mSetupDone = false;
        if (mServiceConn != null) {
            logDebug("Unbinding from service.");
            try {
                mContext.unbindService(mServiceConn);
            } catch (IllegalArgumentException e) {
                logError("Could not unbind: " + e.getMessage());
            }
            mServiceConn = null;
        }
        mService = null;
        mPurchaseListener = null;
    }

    public void disposeWhenFinished() {
        if (!mAsyncInProgress) {
            dispose();
        } else {
            logDebug("Dispose will be called when async operation finishes.");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!mAsyncInProgress) {
                        dispose();
                    } else {
                        handler.postDelayed(this, 1000);
                    }
                }
            }, 1000);
        }
    }

    private void checkSetupDone(String operation) {
        if (!mSetupDone) {
            logError("Illegal state for operation (" + operation + "): IAB helper is not set up.");
            throw new IllegalStateException("IAB helper is not set up.");
        }
    }

    private void checkNotDisposed() {
        if (mServiceConn == null || mService == null) {
            throw new IllegalStateException("IAB helper was disposed of.");
        }
    }

    public boolean subscriptionsSupported() {
        checkSetupDone("subscriptionsSupported");
        return mSubscriptionsSupported;
    }

    public void queryInventoryAsync(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus, QueryInventoryFinishedListener listener) {
        checkSetupDone("queryInventory");
        checkNotDisposed();

        if (mAsyncInProgress) {
            throw new RuntimeException("Cannot start async operation as another is in progress.");
        }
        mAsyncInProgress = true;
        mAsyncOperation = "queryInventory";

        (new Thread(new Runnable() {
            private boolean _querySkuDetails;
            private List<String> _moreItemSkus;
            private List<String> _moreSubsSkus;
            private QueryInventoryFinishedListener _listener;

            public Runnable init(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus, QueryInventoryFinishedListener listener) {
                _querySkuDetails = querySkuDetails;
                _moreItemSkus = moreItemSkus;
                _moreSubsSkus = moreSubsSkus;
                _listener = listener;
                return this;
            }

            @Override
            public void run() {
                IabResult result = new IabResult(BILLING_RESPONSE_RESULT_OK, "Inventory refresh successful.");
                Inventory inv = null;

                try {
                    inv = queryInventory(_querySkuDetails, _moreItemSkus, _moreSubsSkus);
                } catch (IabException e) {
                    result = e.getResult();
                }

                mAsyncInProgress = false;
                mAsyncOperation = "";

                final Inventory inv_f = inv;
                final IabResult result_f = result;
                if (_listener != null) {
                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            _listener.onQueryInventoryFinished(result_f, inv_f);
                        }
                    });
                }
            }
        }.init(querySkuDetails, moreItemSkus, moreSubsSkus, listener))).start();
    }

    private Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        checkSetupDone("queryInventory");
        Inventory inv = new Inventory();

        try {
            Bundle ownedItems = mService.getPurchases(API_VERSION, mContext.getPackageName(), ITEM_TYPE_INAPP, null);
            int response = ownedItems.getInt(RESPONSE_CODE);
            if (response != BILLING_RESPONSE_RESULT_OK) {
                throw new IabException(response, "Failed to query inventory (inapp).");
            }

            ArrayList<String> dataList = ownedItems.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
            ArrayList<String> signatureList = ownedItems.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);

            if (dataList != null) {
                for (int i = 0; i < dataList.size(); i++) {
                    String purchaseData = dataList.get(i);
                    String signature = (signatureList != null && i < signatureList.size()) ? signatureList.get(i) : "";
                    Purchase purchase = new Purchase(ITEM_TYPE_INAPP, purchaseData, signature);
                    if (Security.verifyPurchase(mSignatureBase64, purchaseData, signature)) {
                        inv.addPurchase(purchase);
                    } else {
                        logWarn("Invalid purchase signature.");
                    }
                }
            }

            if (mSubscriptionsSupported) {
                Bundle ownedSubs = mService.getPurchases(API_VERSION, mContext.getPackageName(), ITEM_TYPE_SUBS, null);
                response = ownedSubs.getInt(RESPONSE_CODE);
                if (response != BILLING_RESPONSE_RESULT_OK) {
                    throw new IabException(response, "Failed to query inventory (subs).");
                }

                ArrayList<String> subDataList = ownedSubs.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
                ArrayList<String> subSignatureList = ownedSubs.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);

                if (subDataList != null) {
                    for (int i = 0; i < subDataList.size(); i++) {
                        String purchaseData = subDataList.get(i);
                        String signature = (subSignatureList != null && i < subSignatureList.size()) ? subSignatureList.get(i) : "";
                        Purchase purchase = new Purchase(ITEM_TYPE_SUBS, purchaseData, signature);
                        if (Security.verifyPurchase(mSignatureBase64, purchaseData, signature)) {
                            inv.addPurchase(purchase);
                        } else {
                            logWarn("Invalid subscription signature.");
                        }
                    }
                }
            }

            if (querySkuDetails) {
                if (moreItemSkus != null && !moreItemSkus.isEmpty()) {
                    Bundle skuDetails = getSkuDetails(ITEM_TYPE_INAPP, moreItemSkus);
                    if (skuDetails != null) {
                        ArrayList<String> detailsList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);
                        if (detailsList != null) {
                            for (String thisResponse : detailsList) {
                                SkuDetails d = new SkuDetails(ITEM_TYPE_INAPP, thisResponse);
                                inv.addSkuDetails(d);
                            }
                        }
                    }
                }

                if (mSubscriptionsSupported && moreSubsSkus != null && !moreSubsSkus.isEmpty()) {
                    Bundle skuDetails = getSkuDetails(ITEM_TYPE_SUBS, moreSubsSkus);
                    if (skuDetails != null) {
                        ArrayList<String> detailsList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);
                        if (detailsList != null) {
                            for (String thisResponse : detailsList) {
                                SkuDetails d = new SkuDetails(ITEM_TYPE_SUBS, thisResponse);
                                inv.addSkuDetails(d);
                            }
                        }
                    }
                }
            }

        } catch (RemoteException e) {
            throw new IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while querying inventory.", e);
        } catch (JSONException e) {
            throw new IabException(IABHELPER_BAD_RESPONSE, "JSON exception while querying inventory.", e);
        }

        return inv;
    }

    private Bundle getSkuDetails(String itemType, List<String> skus) throws RemoteException {
        Bundle querySkus = new Bundle();
        querySkus.putStringArrayList("ITEM_ID_LIST", new ArrayList<>(skus));
        return mService.getSkuDetails(API_VERSION, mContext.getPackageName(), itemType, querySkus);
    }

    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode, OnIabPurchaseFinishedListener listener, String extraPayload) {
        checkSetupDone("launchPurchaseFlow");
        checkNotDisposed();

        if (mAsyncInProgress) {
            throw new RuntimeException("Cannot start async operation as another is in progress.");
        }

        if (itemType.equals(ITEM_TYPE_SUBS) && !mSubscriptionsSupported) {
            logError("Subscriptions not supported.");
            if (listener != null) {
                listener.onIabPurchaseFinished(new IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE, "Subscriptions are not available."), null);
            }
            return;
        }

        String payload = extraPayload != null ? extraPayload : "";

        mAsyncInProgress = true;
        mAsyncOperation = "launchPurchaseFlow";
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
        mRequestCode = requestCode;

        try {
            Bundle buyIntentBundle = mService.getBuyIntent(API_VERSION, mContext.getPackageName(), sku, itemType, payload);
            int response = buyIntentBundle.getInt(RESPONSE_CODE);

            if (response == BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                logDebug("Item already owned. Trying to consume first.");
                String token = "";
                try {
                    Inventory inv = queryInventory(false, null, null);
                    Purchase p = inv.getPurchase(sku);
                    if (p != null) token = p.getToken() != null ? p.getToken() : "";
                } catch (Exception e) {
                    logError("Error getting token: " + e.getMessage());
                }
                int consumeResult = mService.consumePurchase(API_VERSION, mContext.getPackageName(), token);
                if (consumeResult == BILLING_RESPONSE_RESULT_OK) {
                    buyIntentBundle = mService.getBuyIntent(API_VERSION, mContext.getPackageName(), sku, itemType, payload);
                    response = buyIntentBundle.getInt(RESPONSE_CODE);
                }
            }

            if (response != BILLING_RESPONSE_RESULT_OK) {
                logError("Unable to buy item. Response: " + getResponseDesc(response));
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(new IabResult(response, getResponseDesc(response)), null);
                }
                mAsyncInProgress = false;
                mAsyncOperation = "";
                return;
            }

            PendingIntent pi = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
            if (pi == null) {
                logError("Could not get buy intent.");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent."), null);
                }
                mAsyncInProgress = false;
                mAsyncOperation = "";
                return;
            }

            act.startIntentSenderForResult(pi.getIntentSender(), requestCode, new Intent(), 0, 0, 0);

        } catch (IntentSender.SendIntentException e) {
            logError("SendIntentException: " + e.getMessage());
            if (mPurchaseListener != null) {
                mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent."), null);
            }
            mAsyncInProgress = false;
            mAsyncOperation = "";
        } catch (RemoteException e) {
            logError("RemoteException: " + e.getMessage());
            if (mPurchaseListener != null) {
                mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception."), null);
            }
            mAsyncInProgress = false;
            mAsyncOperation = "";
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != mRequestCode || mPurchaseListener == null) return false;

        mAsyncInProgress = false;
        mAsyncOperation = "";

        if (data == null) {
            logError("Null data in IAB activity result.");
            mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result."), null);
            return true;
        }

        int responseCode = data.getIntExtra(RESPONSE_CODE, BILLING_RESPONSE_RESULT_OK);
        String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
        String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

        if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
            if (purchaseData == null || dataSignature == null) {
                logError("Could not find purchase data or signature.");
                mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_BAD_RESPONSE, "Missing data."), null);
                return true;
            }

            try {
                Purchase purchase = new Purchase(mPurchasingItemType, purchaseData, dataSignature);
                if (Security.verifyPurchase(mSignatureBase64, purchaseData, dataSignature)) {
                    mPurchaseListener.onIabPurchaseFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Success"), purchase);
                } else {
                    logError("Purchase signature verification failed.");
                    mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_VERIFICATION_FAILED, "Signature verification failed."), null);
                }
            } catch (JSONException e) {
                logError("Could not parse purchase data: " + e.getMessage());
                mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_BAD_RESPONSE, "Could not parse purchase data."), null);
            }
        } else if (resultCode == Activity.RESULT_OK) {
            logError("Purchase failed. Response: " + responseCode);
            mPurchaseListener.onIabPurchaseFinished(new IabResult(responseCode, getResponseDesc(responseCode)), null);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            logDebug("Purchase canceled by user.");
            mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_USER_CANCELLED, "User canceled."), null);
        } else {
            logError("Purchase failed. Result code: " + resultCode);
            mPurchaseListener.onIabPurchaseFinished(new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown response."), null);
        }

        return true;
    }

    public void consumeAsync(Purchase purchase, OnConsumeFinishedListener listener) {
        checkSetupDone("consume");
        checkNotDisposed();

        if (mAsyncInProgress) {
            throw new RuntimeException("Cannot start async operation as another is in progress.");
        }

        if (purchase == null) throw new IllegalArgumentException("Purchase cannot be null.");
        if (purchase.getToken() == null || purchase.getToken().isEmpty()) {
            throw new IllegalArgumentException("Purchase token is empty.");
        }

        mAsyncInProgress = true;
        mAsyncOperation = "consume";

        (new Thread(new Runnable() {
            private Purchase _purchase;
            private OnConsumeFinishedListener _listener;

            public Runnable init(Purchase purchase, OnConsumeFinishedListener listener) {
                _purchase = purchase;
                _listener = listener;
                return this;
            }

            @Override
            public void run() {
                IabResult result;
                try {
                    int response = mService.consumePurchase(API_VERSION, mContext.getPackageName(), _purchase.getToken());
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        result = new IabResult(BILLING_RESPONSE_RESULT_OK, "Consumption successful.");
                    } else {
                        result = new IabResult(response, getResponseDesc(response));
                    }
                } catch (RemoteException e) {
                    result = new IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception while consuming.");
                }

                mAsyncInProgress = false;
                mAsyncOperation = "";

                final IabResult result_f = result;
                if (_listener != null) {
                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            _listener.onConsumeFinished(_purchase, result_f);
                        }
                    });
                }
            }
        }.init(purchase, listener))).start();
    }

    public static String getResponseDesc(int code) {
        switch (code) {
            case BILLING_RESPONSE_RESULT_OK: return "OK";
            case BILLING_RESPONSE_RESULT_USER_CANCELED: return "User Canceled";
            case BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE: return "Service Unavailable";
            case BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE: return "Billing Unavailable";
            case BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE: return "Item Unavailable";
            case BILLING_RESPONSE_RESULT_DEVELOPER_ERROR: return "Developer Error";
            case BILLING_RESPONSE_RESULT_ERROR: return "Error";
            case BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED: return "Item Already Owned";
            case BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED: return "Item Not Owned";
            default: return "Unknown Response (" + code + ")";
        }
    }

    // Listener interfaces
    public interface OnIabSetupFinishedListener {
        void onIabSetupFinished(IabResult result);
    }

    public interface OnIabPurchaseFinishedListener {
        void onIabPurchaseFinished(IabResult result, Purchase purchase);
    }

    public interface OnConsumeFinishedListener {
        void onConsumeFinished(Purchase purchase, IabResult result);
    }

    public interface QueryInventoryFinishedListener {
        void onQueryInventoryFinished(IabResult result, Inventory inventory);
    }

    // IabException
    public static class IabException extends Exception {
        private final IabResult mResult;

        public IabException(IabResult r) { this(r, null); }
        public IabException(int response, String message) { this(new IabResult(response, message)); }
        public IabException(int response, String message, Exception cause) { this(new IabResult(response, message), cause); }
        public IabException(IabResult r, Exception cause) { super(r.getMessage(), cause); mResult = r; }
        public IabResult getResult() { return mResult; }
    }
}
