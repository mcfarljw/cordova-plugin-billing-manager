const exec = require('cordova/exec')

module.exports = {
  acknowledge: function (productId) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'actionAcknowledge', [productId])
      }
    )
  },
  consume: function (productId) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'actionConsume', [productId])
      }
    )
  },
  loadProducts: function (productIds, productType) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'actionLoadProducts', [productIds, productType])
      }
    )
  },
  purchase: function (productId) {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'actionPurchase', [productId])
      }
    )
  },
  restore: function () {
    return new Promise(
      function (resolve, reject) {
        exec(resolve, reject, 'BillingPlugin', 'actionRestore', [])
      }
    )
  },
  onProductLoaded: function (callback) {
    if (typeof callback === 'function') {
      exec(callback, callback, 'BillingPlugin', 'actionOnProductLoaded', [])
    }
  },
  onPurchaseUpdated: function (callback) {
    if (typeof callback === 'function') {
      exec(callback, callback, 'BillingPlugin', 'actionOnPurchaseUpdated', [])
    }
  }
}
