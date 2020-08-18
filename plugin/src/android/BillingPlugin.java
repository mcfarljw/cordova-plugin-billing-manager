package com.jernung.plugins.billing;

import android.content.Intent;
import android.net.Uri;
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
  private CallbackContext productActionCallback;
  private CallbackContext productLoadedCallback;
  private CallbackContext purchaseActionCallback;
  private CallbackContext purchaseLoadedCallback;

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
        actionLoadProducts(args, callbackContext);
        return true;

      case "actionManage":
        actionManage(callbackContext);
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
        sendPluginResult(callbackContext, PluginResult.Status.OK, false);
      } else {
        sendPluginResult(callbackContext, PluginResult.Status.ERROR, billingResult.getResponseCode(), false);
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
        sendPluginResult(callbackContext, PluginResult.Status.OK, purchaseToken, false);
      } else {
        sendPluginResult(callbackContext, PluginResult.Status.ERROR, billingResult.getResponseCode(), false);
      }
    });
  }

  private void actionLoadProducts (JSONArray args, CallbackContext callbackContext) {
    JSONArray productIds;
    String productType;
    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
    List<String> skuList = new ArrayList<>();

    this.productActionCallback = callbackContext;

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

  private void actionManage (CallbackContext callbackContext) {
    cordova.getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")));
    sendPluginResult(callbackContext, PluginResult.Status.OK, false);
  }

  private void actionPurchase (JSONArray args, CallbackContext callbackContext) {
    String productId;
    SkuDetails details;

    this.purchaseActionCallback = callbackContext;

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

    billingClient.launchBillingFlow(cordova.getActivity(), billingFlowParams);
  }

  private void actionOnProductLoaded (CallbackContext callbackContext) {
    this.productLoadedCallback = callbackContext;
  }

  private void actionOnPurchaseUpdated (CallbackContext callbackContext) {
    this.purchaseLoadedCallback = callbackContext;
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
        if (this.purchaseLoadedCallback != null) {
          sendPluginResult(purchaseLoadedCallback, PluginResult.Status.OK, result.getJSONObject(i), false);
        }
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

  private void sendPluginResult (CallbackContext callback, PluginResult.Status status, boolean keepAlive) {
    PluginResult result = new PluginResult(status);

    result.setKeepCallback(keepAlive);

    callback.sendPluginResult(result);
  }

  private void sendPluginResult (CallbackContext callback, PluginResult.Status status, int message, boolean keepAlive) {
    PluginResult result = new PluginResult(status, message);

    result.setKeepCallback(keepAlive);

    callback.sendPluginResult(result);
  }

  private void sendPluginResult (CallbackContext callback, PluginResult.Status status, JSONObject message, boolean keepAlive) {
    PluginResult result = new PluginResult(status, message);

    result.setKeepCallback(keepAlive);

    callback.sendPluginResult(result);
  }

  private void sendPluginResult (CallbackContext callback, PluginResult.Status status, String message, boolean keepAlive) {
    PluginResult result = new PluginResult(status, message);

    result.setKeepCallback(keepAlive);

    callback.sendPluginResult(result);
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

            if (this.purchaseLoadedCallback != null) {
              sendPluginResult(purchaseLoadedCallback, PluginResult.Status.OK, formatPurchaseResponse(purchase), true);
            }

            if (this.purchaseActionCallback != null) {
              sendPluginResult(purchaseActionCallback, PluginResult.Status.OK, formatPurchaseResponse(purchase), false);
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    } else {
      if (this.purchaseActionCallback != null) {
        sendPluginResult(purchaseActionCallback, PluginResult.Status.ERROR, purchaseResult.getResponseCode(), false);
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

            if (this.productLoadedCallback != null) {
              sendPluginResult(productLoadedCallback, PluginResult.Status.OK, formatProductResponse(details), true);
            }

            if (this.productActionCallback != null) {
              sendPluginResult(productActionCallback, PluginResult.Status.OK, formatProductResponse(details), false);
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    } else {
      if (this.productActionCallback != null) {
        sendPluginResult(productActionCallback, PluginResult.Status.ERROR, billingResult.getResponseCode(), false);
      }
    }
  }
}
