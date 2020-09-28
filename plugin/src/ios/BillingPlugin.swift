import StoreKit

@objc(BillingPlugin)
class BillingPlugin : CDVPlugin, SKProductsRequestDelegate, SKPaymentTransactionObserver {
    lazy var loadedProducts: [String: SKProduct] = [:]
    lazy var loadedTransactions: [String: SKPaymentTransaction] = [:]
    lazy var restoredTransactions: [SKPaymentTransaction] = []
    var productRequest = SKProductsRequest()

    var productActionCallback = ""
    var productLoadedCallback = ""
    var purchaseActionCallback = ""
    var purchaseConsumedCallback = ""
    var purchaseRestoredCallback = ""
    var purchaseUpdatedCallback = ""

    override func pluginInitialize() {
        SKPaymentQueue.default().add(self)
    }

    @objc(actionAcknowledge:)
    private func actionAcknowledge(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId
        let productId = command.arguments[0] as? String ?? ""
        let transaction = loadedTransactions[productId];

        if (transaction != nil) {
            SKPaymentQueue.default().finishTransaction(transaction!)

            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: callbackId)
        } else {
            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable to find transaction!"), callbackId: callbackId)
        }
    }

    @objc(actionConsume:)
    private func actionConsume(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId
        let productId = command.arguments[0] as? String ?? ""
        let transaction = loadedTransactions[productId];

        if (transaction != nil) {
            SKPaymentQueue.default().finishTransaction(transaction!)

            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: callbackId)
        } else {
            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable to find transaction!"), callbackId: callbackId)
        }
    }

    @objc(actionLoadProducts:)
    private func actionLoadProducts(command: CDVInvokedUrlCommand) {
        let productIds = command.arguments[0] as? [String] ?? []
        let productIdentifiers = NSSet(array: productIds)

        productActionCallback = command.callbackId

        guard let identifier = productIdentifiers as? Set<String> else {
            sendPluginResult(callbackId: productActionCallback, status: CDVCommandStatus_ERROR, message: "Invalid product identifiers!", keepAlive: false)

            return
        }

        productRequest = SKProductsRequest(productIdentifiers: identifier)
        productRequest.delegate = self
        productRequest.start()
    }

    @objc(actionManage:)
    private func actionManage(command: CDVInvokedUrlCommand) {
        if let url = URL(string: "itms-apps://apps.apple.com/account/subscriptions") {
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url, options: [:])
                sendPluginResult(callbackId: command.callbackId, status: CDVCommandStatus_OK, keepAlive: false)
            } else {
                sendPluginResult(callbackId: command.callbackId, status: CDVCommandStatus_ERROR, message: "Invalid subscriptions management URL!", keepAlive: false)
            }
        } else {
            sendPluginResult(callbackId: command.callbackId, status: CDVCommandStatus_ERROR, message: "Invalid subscriptions management URL!", keepAlive: false)
        }
    }

    @objc(actionPurchase:)
    private func actionPurchase(command: CDVInvokedUrlCommand) {
        let productId = command.arguments[0] as? String ?? ""

        purchaseActionCallback = command.callbackId

        guard let product = loadedProducts[productId] else {
            sendPluginResult(callbackId: command.callbackId, status: CDVCommandStatus_ERROR, message: "Product not found!", keepAlive: false)

            return
        }

        if (SKPaymentQueue.canMakePayments()) {
            SKPaymentQueue.default().add(SKPayment(product: product))
        } else {
            sendPluginResult(callbackId: command.callbackId, status: CDVCommandStatus_ERROR, message: "Unable to make payments!", keepAlive: false)
        }
    }

    @objc(actionOnProductLoaded:)
    private func actionOnProductLoaded(command: CDVInvokedUrlCommand) {
        productLoadedCallback = command.callbackId
    }

    @objc(actionOnPurchaseConsumed:)
    private func actionOnPurchaseConsumed(command: CDVInvokedUrlCommand) {
        purchaseConsumedCallback = command.callbackId
    }

    @objc(actionOnPurchaseUpdated:)
    private func actionOnPurchaseUpdated(command: CDVInvokedUrlCommand) {
        purchaseUpdatedCallback = command.callbackId
    }

    @objc(actionRestore:)
    private func actionRestore(command: CDVInvokedUrlCommand) {
        purchaseRestoredCallback = command.callbackId

        SKPaymentQueue.default().restoreCompletedTransactions()
    }

    private func formatProductResponse (product: SKProduct) -> [String: Any] {
        var formattedProduct: [String: Any] = [:]

        formattedProduct["id"] = product.productIdentifier
        formattedProduct["description"] = product.localizedDescription
        formattedProduct["price"] = "\(product.priceLocale.currencySymbol ?? "$")\(product.price)"
        formattedProduct["priceDecimal"] = product.price
        formattedProduct["title"] = product.localizedTitle

        if #available(iOS 11.2, *) {
            if (product.introductoryPrice != nil) {
                formattedProduct["introductoryPrice"] = product.introductoryPrice?.price ?? nil
                formattedProduct["introductoryPriceCycles"] = product.introductoryPrice?.numberOfPeriods ?? nil
            }
        }

        return formattedProduct
    }

    private func formatTransactionResponse (transaction: SKPaymentTransaction) -> [String: Any] {
        return [
            "id": transaction.payment.productIdentifier,
            "platform": "iOS",
            "receipt": [
                "data": getPaymentReceiptData()!,
                "transactionIdentifier": transaction.transactionIdentifier!
            ],
            "state": transaction.transactionState.rawValue
        ]
    }

    private func getPaymentReceiptData() -> String? {
        do {
            let receiptURL = Bundle.main.appStoreReceiptURL!

            if receiptURL.isFileURL {
                let receiptData = try Data(contentsOf: receiptURL)
                let encodedData = receiptData.base64EncodedString()

                return encodedData
            }

            return nil
        } catch {
            return nil
        }
    }

    func paymentQueue(_ queue: SKPaymentQueue, removedTransactions transactions: [SKPaymentTransaction]) {
        sendPluginResult(callbackId: purchaseActionCallback, status: CDVCommandStatus_ERROR, message: "Payment has been canceled!", keepAlive: false)
    }

    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        let sortedTransactions = transactions.sorted(by: {
            $0.transactionDate!.compare($1.transactionDate!) == .orderedDescending
        })

        restoredTransactions.removeAll()

        for transaction in sortedTransactions {
            loadedTransactions[transaction.payment.productIdentifier] = transaction

            switch (transaction.transactionState) {
            case .deferred:
                sendPluginResult(callbackId: purchaseUpdatedCallback, status: CDVCommandStatus_ERROR, message: "Payment has been deferred!", keepAlive: true)
                sendPluginResult(callbackId: purchaseActionCallback, status: CDVCommandStatus_ERROR, message: "Payment has been deferred!", keepAlive: false)
            case .failed:
                SKPaymentQueue.default().finishTransaction(transaction)
                sendPluginResult(callbackId: purchaseUpdatedCallback, status: CDVCommandStatus_ERROR, message: transaction.error?.localizedDescription ?? "Payment has failed!", keepAlive: true)
                sendPluginResult(callbackId: purchaseActionCallback, status: CDVCommandStatus_ERROR, message: transaction.error?.localizedDescription ?? "Payment has failed!", keepAlive: false)
            case .purchased:
                sendPluginResult(callbackId: purchaseUpdatedCallback, status: CDVCommandStatus_OK, message: formatTransactionResponse(transaction: transaction), keepAlive: true)
                sendPluginResult(callbackId: purchaseActionCallback, status: CDVCommandStatus_OK, message: formatTransactionResponse(transaction: transaction), keepAlive: false)
            case .purchasing:
                break
            case .restored:
                SKPaymentQueue.default().finishTransaction(transaction)
                restoredTransactions.append(transaction)
            @unknown default:
                sendPluginResult(callbackId: purchaseUpdatedCallback, status: CDVCommandStatus_ERROR, message: "Unknown error!", keepAlive: true)
                sendPluginResult(callbackId: purchaseActionCallback, status: CDVCommandStatus_ERROR, message: "Unknown error!", keepAlive: false)
            }
        }
    }

    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        sendPluginResult(callbackId: purchaseRestoredCallback, status: CDVCommandStatus_ERROR, message: "Unable to restore payments!", keepAlive: false)
    }

    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        var formattedPurchases: [Any] = []

        for transaction in restoredTransactions {
            formattedPurchases.append(formatTransactionResponse(transaction: transaction))
        }

        sendPluginResult(callbackId: purchaseRestoredCallback, status: CDVCommandStatus_OK, message: formattedPurchases, keepAlive: false)
    }

    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        var formattedProducts: [Any] = []

        for product in response.products {
            loadedProducts[product.productIdentifier] = product

            formattedProducts.append(formatProductResponse(product: product))
        }

        sendPluginResult(callbackId: productLoadedCallback, status: CDVCommandStatus_OK, message: formattedProducts, keepAlive: true)
        sendPluginResult(callbackId: productActionCallback, status: CDVCommandStatus_OK, message: formattedProducts, keepAlive: false)
    }

    func sendPluginResult(callbackId: String, status: CDVCommandStatus, keepAlive: Bool) {
        if (callbackId.isEmpty) {
            return
        }

        let pluginResult = CDVPluginResult(status: status)

        pluginResult?.setKeepCallbackAs(keepAlive)

        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }

    func sendPluginResult(callbackId: String, status: CDVCommandStatus, message: [Any], keepAlive: Bool) {
        if (callbackId.isEmpty) {
            return
        }

        let pluginResult = CDVPluginResult(status: status, messageAs: message)

        pluginResult?.setKeepCallbackAs(keepAlive)

        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }

    func sendPluginResult(callbackId: String, status: CDVCommandStatus, message: [String : Any], keepAlive: Bool) {
        if (callbackId.isEmpty) {
            return
        }

        let pluginResult = CDVPluginResult(status: status, messageAs: message)

        pluginResult?.setKeepCallbackAs(keepAlive)

        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }

    func sendPluginResult(callbackId: String, status: CDVCommandStatus, message: String, keepAlive: Bool) {
        if (callbackId.isEmpty) {
            return
        }

        let pluginResult = CDVPluginResult(status: status, messageAs: message)

        pluginResult?.setKeepCallbackAs(keepAlive)

        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }
}
