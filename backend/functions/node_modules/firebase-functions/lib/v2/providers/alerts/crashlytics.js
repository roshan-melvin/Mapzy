const require_rolldown_runtime = require('../../../_virtual/rolldown_runtime.js');
const require_common_onInit = require('../../../common/onInit.js');
const require_v2_trace = require('../../trace.js');
const require_v2_providers_alerts_alerts = require('./alerts.js');

//#region src/v2/providers/alerts/crashlytics.ts
var crashlytics_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	getOptsAndApp: () => getOptsAndApp,
	newAnrIssueAlert: () => newAnrIssueAlert,
	newFatalIssueAlert: () => newFatalIssueAlert,
	newNonfatalIssueAlert: () => newNonfatalIssueAlert,
	onNewAnrIssuePublished: () => onNewAnrIssuePublished,
	onNewFatalIssuePublished: () => onNewFatalIssuePublished,
	onNewNonfatalIssuePublished: () => onNewNonfatalIssuePublished,
	onOperation: () => onOperation,
	onRegressionAlertPublished: () => onRegressionAlertPublished,
	onStabilityDigestPublished: () => onStabilityDigestPublished,
	onVelocityAlertPublished: () => onVelocityAlertPublished,
	regressionAlert: () => regressionAlert,
	stabilityDigestAlert: () => stabilityDigestAlert,
	velocityAlert: () => velocityAlert
});
/** @internal */
const newFatalIssueAlert = "crashlytics.newFatalIssue";
/** @internal */
const newNonfatalIssueAlert = "crashlytics.newNonfatalIssue";
/** @internal */
const regressionAlert = "crashlytics.regression";
/** @internal */
const stabilityDigestAlert = "crashlytics.stabilityDigest";
/** @internal */
const velocityAlert = "crashlytics.velocity";
/** @internal */
const newAnrIssueAlert = "crashlytics.newAnrIssue";
/**
* Declares a function that can handle a new fatal issue published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a new fatal issue is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onNewFatalIssuePublished(appIdOrOptsOrHandler, handler) {
	return onOperation(newFatalIssueAlert, appIdOrOptsOrHandler, handler);
}
/**
* Declares a function that can handle a new non-fatal issue published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a new non-fatal issue is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onNewNonfatalIssuePublished(appIdOrOptsOrHandler, handler) {
	return onOperation(newNonfatalIssueAlert, appIdOrOptsOrHandler, handler);
}
/**
* Declares a function that can handle a regression alert published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a regression alert is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onRegressionAlertPublished(appIdOrOptsOrHandler, handler) {
	return onOperation(regressionAlert, appIdOrOptsOrHandler, handler);
}
/**
* Declares a function that can handle a stability digest published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a stability digest is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onStabilityDigestPublished(appIdOrOptsOrHandler, handler) {
	return onOperation(stabilityDigestAlert, appIdOrOptsOrHandler, handler);
}
/**
* Declares a function that can handle a velocity alert published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a velocity alert is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onVelocityAlertPublished(appIdOrOptsOrHandler, handler) {
	return onOperation(velocityAlert, appIdOrOptsOrHandler, handler);
}
/**
* Declares a function that can handle a new Application Not Responding issue published to Crashlytics.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler that is triggered when a new Application Not Responding issue is published to Crashlytics.
* @returns A function that you can export and deploy.
*/
function onNewAnrIssuePublished(appIdOrOptsOrHandler, handler) {
	return onOperation(newAnrIssueAlert, appIdOrOptsOrHandler, handler);
}
/** @internal */
function onOperation(alertType, appIdOrOptsOrHandler, handler) {
	if (typeof appIdOrOptsOrHandler === "function") {
		handler = appIdOrOptsOrHandler;
		appIdOrOptsOrHandler = {};
	}
	const [opts, appId] = getOptsAndApp(appIdOrOptsOrHandler);
	const func = (raw) => {
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(require_v2_providers_alerts_alerts.convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = require_v2_providers_alerts_alerts.getEndpointAnnotation(opts, alertType, appId);
	return func;
}
/**
* Helper function to parse the function opts and appId.
* @internal
*/
function getOptsAndApp(appIdOrOpts) {
	let opts;
	let appId;
	if (typeof appIdOrOpts === "string") {
		opts = {};
		appId = appIdOrOpts;
	} else {
		appId = appIdOrOpts.appId;
		opts = { ...appIdOrOpts };
		delete opts.appId;
	}
	return [opts, appId];
}

//#endregion
Object.defineProperty(exports, 'crashlytics_exports', {
  enumerable: true,
  get: function () {
    return crashlytics_exports;
  }
});
exports.getOptsAndApp = getOptsAndApp;
exports.newAnrIssueAlert = newAnrIssueAlert;
exports.newFatalIssueAlert = newFatalIssueAlert;
exports.newNonfatalIssueAlert = newNonfatalIssueAlert;
exports.onNewAnrIssuePublished = onNewAnrIssuePublished;
exports.onNewFatalIssuePublished = onNewFatalIssuePublished;
exports.onNewNonfatalIssuePublished = onNewNonfatalIssuePublished;
exports.onOperation = onOperation;
exports.onRegressionAlertPublished = onRegressionAlertPublished;
exports.onStabilityDigestPublished = onStabilityDigestPublished;
exports.onVelocityAlertPublished = onVelocityAlertPublished;
exports.regressionAlert = regressionAlert;
exports.stabilityDigestAlert = stabilityDigestAlert;
exports.velocityAlert = velocityAlert;