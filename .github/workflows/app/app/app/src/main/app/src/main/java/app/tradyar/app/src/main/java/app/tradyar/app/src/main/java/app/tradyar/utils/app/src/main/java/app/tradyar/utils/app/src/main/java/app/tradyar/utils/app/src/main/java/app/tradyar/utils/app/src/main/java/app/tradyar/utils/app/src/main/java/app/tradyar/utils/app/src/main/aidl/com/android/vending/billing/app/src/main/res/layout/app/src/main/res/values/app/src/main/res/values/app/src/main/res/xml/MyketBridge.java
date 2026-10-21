package app.tradyar;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.tradyar.utils.IabHelper;
import app.tradyar.utils.IabResult;
import app.tradyar.utils.Inventory;
import app.tradyar.utils.Purchase;

/**
 * MyketBridge.java - JavaScript Bridge for Myket in-app billing.
 *
 * This file connects the JavaScript code in the WebView to the Myket SDK (IabHelper).
 */
public class MyketBridge {
    private static final String TAG = "MyketBridge";

    private static final String BASE_64_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC690jqkiBVMKuEhtalh6QrE5EjQmv5kwV1gHEy+zOtIxQUdtAp24uGcNLv5DCgQFAxs1FzSnzeBBSZldZc51SuwzSC3GfYH16B4AfRnWQhPTecKtH6q18taBUB8pgJRsoXmCbr8ZyDU7kHP51mXzTxQMSZ0plByDnw+MRvFrUjtwIDAQAB";

    private final Activity activity;
    private final WebView webView;

    private IabHelper mHelper;
    private boolean isSetup = false;

    private final Map<String, Purchase> pendingPurchases = new HashMap<>();

    public static final int RC_REQUEST = 10001;

    public MyketBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /**
     * Step 1: startSetup - Connect to Myket billing service
     */
    @JavascriptInterface
    public void startSetup(final String publicKey) {
        Log.d(TAG, "startSetup() called");

        String key = BASE_64_PUBLIC_KEY;
        if (publicKey != null && publicKey.length() > 100) {
            key = publicKey;
        }

        try {
            mHelper = new IabHelper(activity, key);
            mHelper.enableDebugLogging(true);

            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult result) {
                    Log.d(TAG, "Setup finished: " + result);

                    if (result == null || !result.isSuccess()) {
                        Log.e(TAG, "Problem setting up billing: " + result);
                        callJavaScript("myketOnSetupFinished",
                            "{\"success\":false,\"message\":\"" + escapeJson(result != null ? result.getMessage() : "unknown") + "\"}");
                        return;
                    }

                    isSetup = true;
                    Log.d(TAG, "Setup successful. Querying inventory.");
                    callJavaScript("myketOnSetupFinished", "{\"success\":true}");
                    queryInventoryOnStartup();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in startSetup", e);
            callJavaScript("myketOnSetupFinished",
                "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @JavascriptInterface
    public boolean isSetup() {
        return isSetup && mHelper != null;
    }

    /**
     * Step 2: queryInventoryOnStartup - Check for unconsumed purchases
     */
    private void queryInventoryOnStartup() {
        if (mHelper == null || !isSetup) return;

        List<String> itemSkus = new ArrayList<>();
        itemSkus.add("tradyar_coins_p1");
        itemSkus.add("tradyar_coins_p2");
        itemSkus.add("tradyar_coins_p3");
        itemSkus.add("tradyar_sub_sh1");
        itemSkus.add("tradyar_sub_sh2");
        itemSkus.add("tradyar_sub_sh3");
        itemSkus.add("tradyar_sub_sh4");
        itemSkus.add("tradyar_sub_sh5");
        itemSkus.add("tradyar_sub_s1");
        itemSkus.add("tradyar_sub_s2");
        itemSkus.add("tradyar_sub_s3");

        List<String> subSkus = new ArrayList<>();

        try {
            mHelper.queryInventoryAsync(true, itemSkus, subSkus, mGotInventoryListener);
        } catch (Exception e) {
            Log.e(TAG, "Error querying inventory", e);
        }
    }

    @JavascriptInterface
    public void queryInventoryAsync(boolean querySkuDetails, String itemSkusJson, String subSkusJson, final String callback) {
        Log.d(TAG, "queryInventoryAsync called");

        if (mHelper == null || !isSetup) {
            callJavaScript(callback, "null");
            return;
        }

        try {
            List<String> itemSkus = parseSkus(itemSkusJson);
            List<String> subSkus = parseSkus(subSkusJson);

            mHelper.queryInventoryAsync(querySkuDetails, itemSkus, subSkus,
                new IabHelper.QueryInventoryFinishedListener() {
                    @Override
                    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                        if (mHelper == null) return;

                        if (result == null || result.isFailure()) {
                            callJavaScript(callback,
                                "{\"success\":false,\"message\":\"" + escapeJson(result != null ? result.getMessage() : "unknown") + "\"}");
                            return;
                        }

                        try {
                            JSONObject json = new JSONObject();
                            json.put("success", true);

                            JSONArray purchasesArray = new JSONArray();
                            if (inventory != null) {
                                List<Purchase> purchases = inventory.getAllPurchases();
                                if (purchases != null) {
                                    for (Purchase purchase : purchases) {
                                        JSONObject p = new JSONObject();
                                        p.put("sku", purchase.getSku());
                                        p.put("token", purchase.getToken());
                                        p.put("orderId", purchase.getOrderId());
                                        p.put("purchaseTime", purchase.getPurchaseTime());
                                        p.put("purchaseState", purchase.getPurchaseState());
                                        p.put("developerPayload", purchase.getDeveloperPayload());
                                        p.put("originalJson", purchase.getOriginalJson());
                                        p.put("signature", purchase.getSignature());
                                        purchasesArray.put(p);
                                    }
                                }
                            }
                            json.put("purchases", purchasesArray);

                            callJavaScript(callback, json.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error building inventory JSON", e);
                            callJavaScript(callback,
                                "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                        }
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in queryInventoryAsync", e);
            callJavaScript(callback,
                "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Step 3: launchPurchaseFlow - Open the Myket payment dialog
     */
    @JavascriptInterface
    public void launchPurchaseFlow(String sku, String itemType, final String developerPayload, final String callback) {
        Log.d(TAG, "launchPurchaseFlow: " + sku);

        if (mHelper == null || !isSetup) {
            callJavaScript(callback, "{\"success\":false,\"error\":\"not_setup\"}");
            return;
        }

        try {
            String type = IabHelper.ITEM_TYPE_INAPP;
            if ("subs".equals(itemType)) {
                type = IabHelper.ITEM_TYPE_SUBS;
            }

            // Set the request code before launching
            mHelper.setRequestCode(RC_REQUEST);

            mHelper.launchPurchaseFlow(activity, sku, type, RC_REQUEST,
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        @Override
                        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                            Log.d(TAG, "Purchase finished: " + result);

                            if (mHelper == null) return;

                            if (result == null || result.isFailure()) {
                                boolean cancelled = (result != null && result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED);
                                String errorJson = "{\"success\":false,\"error\":\"" +
                                    (cancelled ? "cancelled" : "purchase_failed") +
                                    "\",\"message\":\"" + escapeJson(result != null ? result.getMessage() : "unknown") +
                                    "\",\"cancelled\":" + cancelled + "}";
                                callJavaScript(callback, errorJson);
                                return;
                            }

                            // Store purchase for later consume
                            if (purchase != null) {
                                String token = purchase.getToken();
                                if (token != null) {
                                    pendingPurchases.put(token, purchase);
                                    Log.d(TAG, "Purchase stored for consume. Token: " + token);
                                }
                            }

                            try {
                                JSONObject json = new JSONObject();
                                json.put("success", true);
                                json.put("isSuccess", true);

                                if (purchase != null) {
                                    JSONObject purchaseJson = new JSONObject();
                                    purchaseJson.put("sku", purchase.getSku());
                                    purchaseJson.put("token", purchase.getToken());
                                    purchaseJson.put("orderId", purchase.getOrderId());
                                    purchaseJson.put("itemType", purchase.getItemType());
                                    purchaseJson.put("purchaseTime", purchase.getPurchaseTime());
                                    purchaseJson.put("purchaseState", purchase.getPurchaseState());
                                    purchaseJson.put("developerPayload", purchase.getDeveloperPayload());
                                    purchaseJson.put("originalJson", purchase.getOriginalJson());
                                    purchaseJson.put("signature", purchase.getSignature());
                                    json.put("purchase", purchaseJson);
                                }

                                callJavaScript(callback, json.toString());
                            } catch (Exception e) {
                                Log.e(TAG, "Error building purchase JSON", e);
                                callJavaScript(callback, "{\"success\":true}");
                            }
                        }
                    }, developerPayload);
        } catch (Exception e) {
            Log.e(TAG, "Error in launchPurchaseFlow", e);
            callJavaScript(callback,
                "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Step 4: consumeAsync - Consume a consumable purchase
     */
    @JavascriptInterface
    public void consumeAsync(String token, final String callback) {
        Log.d(TAG, "consumeAsync: " + token);

        if (mHelper == null || !isSetup) {
            callJavaScript(callback, "{\"success\":false}");
            return;
        }

        if (token == null || token.isEmpty()) {
            callJavaScript(callback, "{\"success\":false,\"error\":\"invalid_token\"}");
            return;
        }

        Purchase purchase = pendingPurchases.get(token);
        if (purchase == null) {
            Log.w(TAG, "Purchase not found for token: " + token);
            callJavaScript(callback, "{\"success\":false,\"error\":\"purchase_not_found\"}");
            return;
        }

        try {
            mHelper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {
                @Override
                public void onConsumeFinished(Purchase purchase, IabResult result) {
                    Log.d(TAG, "Consumption finished: " + result);

                    if (mHelper == null) return;

                    if (result != null && result.isSuccess()) {
                        callJavaScript(callback, "{\"success\":true,\"isSuccess\":true}");
                        if (purchase != null && purchase.getToken() != null) {
                            pendingPurchases.remove(purchase.getToken());
                        }
                    } else {
                        callJavaScript(callback,
                            "{\"success\":false,\"message\":\"" + escapeJson(result != null ? result.getMessage() : "unknown") + "\"}");
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in consumeAsync", e);
            callJavaScript(callback,
                "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener =
        new IabHelper.QueryInventoryFinishedListener() {
            @Override
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                if (mHelper == null) return;

                if (result == null || result.isFailure()) {
                    callJavaScript("myketOnQueryInventoryFinished", "{\"success\":false}");
                    return;
                }

                Log.d(TAG, "Initial inventory query successful");

                try {
                    JSONObject json = new JSONObject();
                    json.put("success", true);

                    JSONArray purchasesArray = new JSONArray();
                    if (inventory != null) {
                        List<Purchase> purchases = inventory.getAllPurchases();
                        if (purchases != null) {
                            for (Purchase purchase : purchases) {
                                JSONObject p = new JSONObject();
                                p.put("sku", purchase.getSku());
                                p.put("token", purchase.getToken());
                                p.put("orderId", purchase.getOrderId());
                                p.put("purchaseTime", purchase.getPurchaseTime());
                                p.put("purchaseState", purchase.getPurchaseState());
                                p.put("developerPayload", purchase.getDeveloperPayload());
                                p.put("originalJson", purchase.getOriginalJson());
                                p.put("signature", purchase.getSignature());
                                purchasesArray.put(p);
                            }
                        }
                    }
                    json.put("purchases", purchasesArray);

                    callJavaScript("myketOnQueryInventoryFinished", json.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Error building inventory JSON", e);
                }
            }
        };

    private List<String> parseSkus(String json) {
        List<String> skus = new ArrayList<>();
        if (json == null || json.isEmpty()) return skus;
        try {
            if (json.trim().startsWith("[")) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    skus.add(array.getString(i));
                }
            } else {
                String[] parts = json.split(",");
                for (String part : parts) {
                    String trimmed = part.trim().replace("\"", "");
                    if (!trimmed.isEmpty()) {
                        skus.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing skus: " + json, e);
        }
        return skus;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "\\r")
               .replace("\t", "\\t");
    }

    private void callJavaScript(final String functionName, final String data) {
        if (activity == null || webView == null) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String js;
                    if (functionName != null && !functionName.isEmpty()) {
                        if (functionName.startsWith("myket") ||
                            functionName.equals("myketOnSetupFinished") ||
                            functionName.equals("myketOnPurchaseFinished") ||
                            functionName.equals("myketOnConsumeFinished") ||
                            functionName.equals("myketOnQueryInventoryFinished")) {
                            js = "javascript:" + functionName + "(" + data + ");";
                        } else {
                            js = "javascript:if(window." + functionName + "){window." +
                                 functionName + "(" + data + ");window." + functionName + "=null;}";
                        }
                        Log.d(TAG, "Calling JS: " + js);
                        webView.evaluateJavascript(js, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error calling JavaScript", e);
                }
            }
        });
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (mHelper == null) return false;
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    public void onDestroy() {
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            try {
                mHelper.disposeWhenFinished();
            } catch (Exception e) {
                Log.e(TAG, "Error disposing helper", e);
            }
            mHelper = null;
        }
        isSetup = false;
        pendingPurchases.clear();
    }
}
