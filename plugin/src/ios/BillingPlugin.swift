import StoreKit

@objc(BillingPlugin)
class BillingPlugin : CDVPlugin, SKProductsRequestDelegate, SKPaymentTransactionObserver {
    lazy var loadedProducts: [String: SKProduct] = [:]
    lazy var loadedTransactions: [String: SKPaymentTransaction] = [:]
    var productRequest = SKProductsRequest()

    var productLoadedCallback = ""
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
            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable find find transaction!"), callbackId: callbackId)
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
            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable find find transaction!"), callbackId: callbackId)
        }
    }

    @objc(actionLoadProducts:)
    private func actionLoadProducts(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId
        let productIds = command.arguments[0] as? [String] ?? []
        let productIdentifiers = NSSet(array: productIds)
        guard let identifier = productIdentifiers as? Set<String> else {
            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable find find transaction!"), callbackId: callbackId)

            return
        }

        productRequest = SKProductsRequest(productIdentifiers: identifier)
        productRequest.delegate = self
        productRequest.start()

        self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: callbackId)
    }

    @objc(actionPurchase:)
    private func actionPurchase(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId
        let productId = command.arguments[0] as? String ?? ""

        guard let product = loadedProducts[productId] else {
            self.commandDelegate.send(
                CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Product not found!"),
                callbackId: command.callbackId
            )

            return
        }

        if (SKPaymentQueue.canMakePayments()) {
            let payment = SKPayment(product: product)

            SKPaymentQueue.default().add(payment)

            self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: callbackId)
        } else {
            self.commandDelegate.send(
                CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable to make payments!"),
                callbackId: command.callbackId
            )
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
        formattedProduct["price"] = product.price
        formattedProduct["title"] = product.localizedTitle


        if #available(iOS 11.2, *) {
            formattedProduct["introductoryPrice"] = product.introductoryPrice?.price ?? nil
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

    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            loadedTransactions[transaction.payment.productIdentifier] = transaction

            switch (transaction.transactionState) {
            case .deferred:
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)

                pluginResult?.setKeepCallbackAs(true)

                self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback)
            case .failed:
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: transaction.error?.localizedDescription)

                pluginResult?.setKeepCallbackAs(true)

                SKPaymentQueue.default().finishTransaction(transaction)

                self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback)
            case .purchased:
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: formatTransactionResponse(transaction: transaction))

                pluginResult?.setKeepCallbackAs(true)

                self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback )
            case .purchasing:
                break
            case .restored:
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: formatTransactionResponse(transaction: transaction))

                pluginResult?.setKeepCallbackAs(true)

                SKPaymentQueue.default().finishTransaction(transaction)

                self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback )
            @unknown default:
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unknown error!")

                pluginResult?.setKeepCallbackAs(true)

                self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback)
            }
        }
    }

    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Unable to restore payments!"), callbackId: purchaseRestoredCallback)
    }

    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: purchaseRestoredCallback)
    }

    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        var formattedProducts: [Any] = []

        for product in response.products {
            loadedProducts[product.productIdentifier] = product

            formattedProducts.append(formatProductResponse(product: product))
        }

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: formattedProducts)

        pluginResult?.setKeepCallbackAs(true)

        self.commandDelegate.send(pluginResult, callbackId: purchaseUpdatedCallback)
    }
}
