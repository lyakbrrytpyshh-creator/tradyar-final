package app.tradyar.utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class Inventory {
    Map<String, SkuDetails> mSkuMap = new HashMap<>();
    Map<String, Purchase> mPurchaseMap = new HashMap<>();
    public Inventory() {}
    public SkuDetails getSkuDetails(String sku) { return mSkuMap.get(sku); }
    public Purchase getPurchase(String sku) { return mPurchaseMap.get(sku); }
    public boolean hasPurchase(String sku) { return mPurchaseMap.containsKey(sku); }
    public void erasePurchase(String sku) { if (mPurchaseMap.containsKey(sku)) mPurchaseMap.remove(sku); }
    public List<String> getAllOwnedSkus() { return new ArrayList<>(mPurchaseMap.keySet()); }
    public List<Purchase> getAllPurchases() { return new ArrayList<>(mPurchaseMap.values()); }
    public void addSkuDetails(SkuDetails d) { mSkuMap.put(d.getSku(), d); }
    public void addPurchase(Purchase p) { mPurchaseMap.put(p.getSku(), p); }
}
