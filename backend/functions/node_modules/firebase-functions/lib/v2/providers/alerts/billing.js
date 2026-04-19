const require_rolldown_runtime = require('../../../_virtual/rolldown_runtime.js');
const require_common_onInit = require('../../../common/onInit.js');
const require_v2_trace = require('../../trace.js');
const require_v2_providers_alerts_alerts = require('./alerts.js');

//#region src/v2/providers/alerts/billing.ts
var billing_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	onOperation: () => onOperation,
	onPlanAutomatedUpdatePublished: () => onPlanAutomatedUpdatePublished,
	onPlanUpdatePublished: () => onPlanUpdatePublished,
	planAutomatedUpdateAlert: () => planAutomatedUpdateAlert,
	planUpdateAlert: () => planUpdateAlert
});
/** @internal */
const planUpdateAlert = "billing.planUpdate";
/** @internal */
const planAutomatedUpdateAlert = "billing.planAutomatedUpdate";
/**
* Declares a function that can handle a billing plan update event.
* @param optsOrHandler - Options or an event-handling function.
* @param handler - Event handler which is run every time a billing plan is updated.
* @returns A function that you can export and deploy.
*/
function onPlanUpdatePublished(optsOrHandler, handler) {
	return onOperation(planUpdateAlert, optsOrHandler, handler);
}
/**
* Declares a function that can handle an automated billing plan update event.
* @param optsOrHandler - Options or an event-handling function.
* @param handler - Event handler which is run every time an automated billing plan update occurs.
* @returns A function that you can export and deploy.
*/
function onPlanAutomatedUpdatePublished(optsOrHandler, handler) {
	return onOperation(planAutomatedUpdateAlert, optsOrHandler, handler);
}
/** @internal */
function onOperation(alertType, optsOrHandler, handler) {
	if (typeof optsOrHandler === "function") {
		handler = optsOrHandler;
		optsOrHandler = {};
	}
	const func = (raw) => {
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(require_v2_providers_alerts_alerts.convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = require_v2_providers_alerts_alerts.getEndpointAnnotation(optsOrHandler, alertType);
	return func;
}

//#endregion
Object.defineProperty(exports, 'billing_exports', {
  enumerable: true,
  get: function () {
    return billing_exports;
  }
});
exports.onOperation = onOperation;
exports.onPlanAutomatedUpdatePublished = onPlanAutomatedUpdatePublished;
exports.onPlanUpdatePublished = onPlanUpdatePublished;
exports.planAutomatedUpdateAlert = planAutomatedUpdateAlert;
exports.planUpdateAlert = planUpdateAlert;