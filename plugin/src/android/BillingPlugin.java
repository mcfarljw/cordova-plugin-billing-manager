package com.jernung.plugins.billing;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class BillingPlugin extends CordovaPlugin implements BillingClientStateListener, PurchasesUpdatedListener, SkuDetailsResponseListener {

  private static final String PLUGIN_NAME = "Billing";
  private BillingClient billingClient;
  private HashMap<String, SkuDetails> loadedProducts = new HashMap<>();
  private HashMap<String, Purchase> loadedPurchases = new HashMap<>();
  private CallbackContext productCallback;
  private CallbackContext purchaseCallback;

  public void initialize (CordovaInterface cordova, CordovaWebView webview) {
    super.initialize(cordova, webview);

    billingClient = BillingClient.newBuilder(cordova.getActivity())
      .setListener(this)
      .enablePendingPurchases()
      .build();

    billingClient.startConnection(this);
  }

  public boolean execute (String action, JSONArray args, CallbackContext callbackContext) {
    switch (action) {
      case "actionAcknowledge":
        actionAcknowledge(args, callbackContext);
        return true;

      case "actionConsume":
        actionConsume(args, callbackContext);
        return true;

      case "actionLoadProducts":
        actionLoadProducts(args);
        return true;

      case "actionPurchase":
        actionPurchase(args, callbackContext);
        return true;

      case "actionRestore":
        actionRestore();
        return true;

      case "actionOnProductLoaded":
        actionOnProductLoaded(callbackContext);
        return true;

      case "actionOnPurchaseUpdated":
        actionOnPurchaseUpdated(callbackContext);
        return true;
    }

    return false;
  }

  private void actionAcknowledge (JSONArray args, CallbackContext callbackContext) {
    String productId;
    Purchase purchase;

    try {
      productId = args.getString(0);
      purchase = loadedPurchases.get(productId);
    } catch (JSONException error) {
      return;
    }

    if (purchase == null) {
      return;
    }

    AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
      .setPurchaseToken(purchase.getPurchaseToken())
      .build();

    billingClient.acknowledgePurchase(acknowledgePurchaseParams, (@NonNull BillingResult billingResult) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
      } else {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, billingResult.getResponseCode()));
      }
    });
  }

  private void actionConsume (JSONArray args, CallbackContext callbackContext) {
    String productId;
    Purchase purchase;

    try {
      productId = args.getString(0);
      purchase = loadedPurchases.get(productId);
    } catch (JSONException error) {
      return;
    }

    if (purchase == null) {
      return;
    }

    ConsumeParams consumeParams = ConsumeParams.newBuilder()
      .setPurchaseToken(purchase.getPurchaseToken())
      .build();

    billingClient.consumeAsync(consumeParams, (@NonNull BillingResult billingResult, @NonNull String purchaseToken) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, purchaseToken));
      } else {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, billingResult.getResponseCode()));
      }
    });
  }

  private void actionLoadProducts (JSONArray args) {
    JSONArray productIds;
    String productType;
    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
    List<String> skuList = new ArrayList<>();

    try {
      productIds = args.getJSONArray(0);
      productType = args.getString(1).toLowerCase();
    } catch (JSONException error) {
      return;
    }

    if (!productType.equals(BillingClient.SkuType.INAPP) && !productType.equals(BillingClient.SkuType.SUBS)) {
      return;
    }

    for (int i = 0 ; i < productIds.length(); i++) {
      try {
        skuList.add(productIds.getString(i));
      } catch (JSONException error) {
        Log.d(PLUGIN_NAME, Objects.requireNonNull(error.getMessage()));
      }
    }

    params.setSkusList(skuList);
    params.setType(productType);

    billingClient.querySkuDetailsAsync(params.build(), this);
  }

  private void actionPurchase (JSONArray args, CallbackContext callbackContext) {
    String productId;
    SkuDetails details;

    try {
      productId = args.getString(0);
      details = loadedProducts.get(productId);
    } catch (JSONException error) {
      return;
    }

    if (details == null) {
      return;
    }

    BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
      .setSkuDetails(details)
      .build();

    BillingResult result = billingClient.launchBillingFlow(cordova.getActivity(), billingFlowParams);

    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, result.getResponseCode()));
    }
  }

  private void actionOnProductLoaded (CallbackContext callbackContext) {
    this.productCallback = callbackContext;
  }

  private void actionOnPurchaseUpdated (CallbackContext callbackContext) {
    this.purchaseCallback = callbackContext;
  }

  private void actionRestore () {
    JSONArray result = new JSONArray();
    Purchase.PurchasesResult inappResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
    Purchase.PurchasesResult subsResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
    PluginResult pluginResult;

    if (inappResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      List<Purchase> inappPurchases = inappResult.getPurchasesList();

      if (inappPurchases != null) {
        for (Purchase purchase : inappPurchases) {
          try {
            loadedPurchases.put(purchase.getSku(), purchase);
            result.put(formatPurchaseResponse(purchase));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    }

    if (subsResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      List<Purchase> subsPurchases = subsResult.getPurchasesList();

      if (subsPurchases != null) {
        for (Purchase purchase : subsPurchases) {
          try {
            loadedPurchases.put(purchase.getSku(), purchase);
            result.put(formatPurchaseResponse(purchase));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    }

    for (int i = 0; i < result.length(); i++) {
      try {
        Log.d(PLUGIN_NAME, result.getJSONObject(i).toString());
        pluginResult = new PluginResult(PluginResult.Status.OK, result.getJSONObject(i));
        pluginResult.setKeepCallback(true);

        purchaseCallback.sendPluginResult(pluginResult);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   *
   * @param details
   * @return
   * @throws JSONException
   */
  private JSONObject formatProductResponse (SkuDetails details) throws JSONException {
    JSONObject response = new JSONObject();

    response.put("id", details.getSku());
    response.put("description", details.getDescription());

    response.put("price", details.getPrice());
    response.put("title", details.getTitle());

    if (!details.getIntroductoryPrice().isEmpty()) {
      response.put("introductoryPrice", details.getIntroductoryPrice());
      response.put("introductoryPriceAmount", details.getIntroductoryPriceAmountMicros());
    }

    return response;
  }

  /**
   *
   * @param purchase
   * @return
   * @throws JSONException
   */
  private JSONObject formatPurchaseResponse (Purchase purchase) throws JSONException {
    JSONObject response = new JSONObject();
    JSONObject receipt = new JSONObject();


    receipt.put("acknowledged", purchase.isAcknowledged());
    receipt.put("orderId", purchase.getOrderId());
    receipt.put("packageName", purchase.getPackageName());
    receipt.put("purchaseToken", purchase.getPurchaseToken());

    response.put("id", purchase.getSku());
    response.put("platform", "Android");
    response.put("receipt", receipt);
    response.put("state", purchase.getPurchaseState());

    return response;
  }

  @Override
  public void onBillingSetupFinished(@NonNull BillingResult billingResult) {}

  @Override
  public void onBillingServiceDisconnected() {}

  @Override
  public void onPurchasesUpdated(@NonNull BillingResult purchaseResult, @Nullable List<Purchase> purchases) {
    PluginResult pluginResult;

    if (purchaseResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      if (purchases != null) {
        for (Purchase purchase : purchases) {
          loadedPurchases.put(purchase.getSku(), purchase);

          try {
            pluginResult = new PluginResult(PluginResult.Status.OK, formatPurchaseResponse(purchase));
            pluginResult.setKeepCallback(true);

            if (this.purchaseCallback != null) {
              purchaseCallback.sendPluginResult(pluginResult);
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  @Override
  public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {
    PluginResult pluginResult;

    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      if (list != null) {
        for (SkuDetails details : list) {
          loadedProducts.put(details.getSku(), details);

          try {
            pluginResult = new PluginResult(PluginResult.Status.OK, formatProductResponse(details));
            pluginResult.setKeepCallback(true);

            if (this.productCallback != null) {
              productCallback.sendPluginResult(pluginResult);
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }
}
