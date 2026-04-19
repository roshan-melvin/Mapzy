const require_rolldown_runtime = require('../../../_virtual/rolldown_runtime.js');
const require_common_onInit = require('../../../common/onInit.js');
const require_v2_trace = require('../../trace.js');
const require_v2_providers_alerts_alerts = require('./alerts.js');

//#region src/v2/providers/alerts/performance.ts
var performance_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	convertPayload: () => convertPayload,
	getOptsAndApp: () => getOptsAndApp,
	onThresholdAlertPublished: () => onThresholdAlertPublished,
	thresholdAlert: () => thresholdAlert
});
/** @internal */
const thresholdAlert = "performance.threshold";
/**
* Declares a function that can handle receiving performance threshold alerts.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler which is run every time a threshold alert is received.
* @returns A function that you can export and deploy.
*/
function onThresholdAlertPublished(appIdOrOptsOrHandler, handler) {
	if (typeof appIdOrOptsOrHandler === "function") {
		handler = appIdOrOptsOrHandler;
		appIdOrOptsOrHandler = {};
	}
	const [opts, appId] = getOptsAndApp(appIdOrOptsOrHandler);
	const func = (raw) => {
		const event = require_v2_providers_alerts_alerts.convertAlertAndApp(raw);
		const convertedPayload = convertPayload(event.data.payload);
		event.data.payload = convertedPayload;
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler(event)));
	};
	func.run = handler;
	func.__endpoint = require_v2_providers_alerts_alerts.getEndpointAnnotation(opts, thresholdAlert, appId);
	return func;
}
/**
* Helper function to parse the function opts and appId.
* @internal
*/
function getOptsAndApp(appIdOrOpts) {
	if (typeof appIdOrOpts === "string") {
		return [{}, appIdOrOpts];
	}
	const opts = { ...appIdOrOpts };
	const appId = appIdOrOpts.appId;
	delete opts.appId;
	return [opts, appId];
}
/**
* Helper function to convert the raw payload of a {@link PerformanceEvent} to a {@link ThresholdAlertPayload}
* @internal
*/
function convertPayload(raw) {
	const payload = { ...raw };
	if (typeof payload.conditionPercentile !== "undefined" && payload.conditionPercentile === 0) {
		delete payload.conditionPercentile;
	}
	if (typeof payload.appVersion !== "undefined" && payload.appVersion.length === 0) {
		delete payload.appVersion;
	}
	return payload;
}

//#endregion
exports.convertPayload = convertPayload;
exports.getOptsAndApp = getOptsAndApp;
exports.onThresholdAlertPublished = onThresholdAlertPublished;
Object.defineProperty(exports, 'performance_exports', {
  enumerable: true,
  get: function () {
    return performance_exports;
  }
});
exports.thresholdAlert = thresholdAlert;