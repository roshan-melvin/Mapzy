const require_rolldown_runtime = require('../../../_virtual/rolldown_runtime.js');
const require_v2_providers_alerts_alerts = require('./alerts.js');
const require_v2_providers_alerts_appDistribution = require('./appDistribution.js');
const require_v2_providers_alerts_billing = require('./billing.js');
const require_v2_providers_alerts_crashlytics = require('./crashlytics.js');
const require_v2_providers_alerts_performance = require('./performance.js');

//#region src/v2/providers/alerts/index.ts
var alerts_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	appDistribution: () => require_v2_providers_alerts_appDistribution.appDistribution_exports,
	billing: () => require_v2_providers_alerts_billing.billing_exports,
	convertAlertAndApp: () => require_v2_providers_alerts_alerts.convertAlertAndApp,
	crashlytics: () => require_v2_providers_alerts_crashlytics.crashlytics_exports,
	eventType: () => require_v2_providers_alerts_alerts.eventType,
	getEndpointAnnotation: () => require_v2_providers_alerts_alerts.getEndpointAnnotation,
	getOptsAndAlertTypeAndApp: () => require_v2_providers_alerts_alerts.getOptsAndAlertTypeAndApp,
	onAlertPublished: () => require_v2_providers_alerts_alerts.onAlertPublished,
	performance: () => require_v2_providers_alerts_performance.performance_exports
});

//#endregion
Object.defineProperty(exports, 'alerts_exports', {
  enumerable: true,
  get: function () {
    return alerts_exports;
  }
});
Object.defineProperty(exports, 'appDistribution', {
  enumerable: true,
  get: function () {
    return require_v2_providers_alerts_appDistribution.appDistribution_exports;
  }
});
Object.defineProperty(exports, 'billing', {
  enumerable: true,
  get: function () {
    return require_v2_providers_alerts_billing.billing_exports;
  }
});
exports.convertAlertAndApp = require_v2_providers_alerts_alerts.convertAlertAndApp;
Object.defineProperty(exports, 'crashlytics', {
  enumerable: true,
  get: function () {
    return require_v2_providers_alerts_crashlytics.crashlytics_exports;
  }
});
exports.eventType = require_v2_providers_alerts_alerts.eventType;
exports.getEndpointAnnotation = require_v2_providers_alerts_alerts.getEndpointAnnotation;
exports.getOptsAndAlertTypeAndApp = require_v2_providers_alerts_alerts.getOptsAndAlertTypeAndApp;
exports.onAlertPublished = require_v2_providers_alerts_alerts.onAlertPublished;
Object.defineProperty(exports, 'performance', {
  enumerable: true,
  get: function () {
    return require_v2_providers_alerts_performance.performance_exports;
  }
});