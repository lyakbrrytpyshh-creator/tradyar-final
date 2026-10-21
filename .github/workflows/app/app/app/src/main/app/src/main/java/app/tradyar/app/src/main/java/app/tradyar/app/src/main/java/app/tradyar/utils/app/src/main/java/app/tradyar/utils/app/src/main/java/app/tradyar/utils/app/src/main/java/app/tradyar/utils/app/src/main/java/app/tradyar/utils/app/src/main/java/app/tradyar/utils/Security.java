package app.tradyar.utils;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
public class Security {
    private static final String TAG = "IABUtil/Security";
    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
    public static PublicKey generatePublicKey(String encodedPublicKey) {
        try {
            byte[] decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (Exception e) { Log.e(TAG, "Error generating key: " + e.getMessage()); return null; }
    }
    public static boolean verify(PublicKey publicKey, String signedData, String signature) {
        if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(signature)) return false;
        try {
            byte[] signatureBytes = Base64.decode(signature, Base64.DEFAULT);
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            return sig.verify(signatureBytes);
        } catch (Exception e) { Log.e(TAG, "Verification failed: " + e.getMessage()); return false; }
    }
    public static boolean verifyPurchase(String base64PublicKey, String signedData, String signature) {
        if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey) || TextUtils.isEmpty(signature)) return false;
        PublicKey key = generatePublicKey(base64PublicKey);
        return verify(key, signedData, signature);
    }
}
