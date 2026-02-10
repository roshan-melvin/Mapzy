const require_runtime_manifest = require('../../../runtime/manifest.js');
const require_common_onInit = require('../../../common/onInit.js');
const require_v2_trace = require('../../trace.js');
const require_v2_options = require('../../options.js');

//#region src/v2/providers/alerts/alerts.ts
/** @internal */
const eventType = "google.firebase.firebasealerts.alerts.v1.published";
function onAlertPublished(alertTypeOrOpts, handler) {
	const [opts, alertType, appId] = getOptsAndAlertTypeAndApp(alertTypeOrOpts);
	const func = (raw) => {
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = getEndpointAnnotation(opts, alertType, appId);
	return func;
}
/**
* Helper function for getting the endpoint annotation used in alert handling functions.
* @internal
*/
function getEndpointAnnotation(opts, alertType, appId) {
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	const endpoint = {
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		eventTrigger: {
			eventType,
			eventFilters: { alerttype: alertType },
			retry: opts.retry ?? false
		}
	};
	if (appId) {
		endpoint.eventTrigger.eventFilters.appid = appId;
	}
	return endpoint;
}
/**
* Helper function to parse the function opts, alert type, and appId.
* @internal
*/
function getOptsAndAlertTypeAndApp(alertTypeOrOpts) {
	let opts;
	let alertType;
	let appId;
	if (typeof alertTypeOrOpts === "string") {
		alertType = alertTypeOrOpts;
		opts = {};
	} else {
		alertType = alertTypeOrOpts.alertType;
		appId = alertTypeOrOpts.appId;
		opts = { ...alertTypeOrOpts };
		delete opts.alertType;
		delete opts.appId;
	}
	return [
		opts,
		alertType,
		appId
	];
}
/**
* Helper function to covert alert type & app id in the CloudEvent to camel case.
* @internal
*/
function convertAlertAndApp(raw) {
	const event = { ...raw };
	if ("alerttype" in event) {
		event.alertType = event.alerttype;
	}
	if ("appid" in event) {
		event.appId = event.appid;
	}
	return event;
}

//#endregion
exports.convertAlertAndApp = convertAlertAndApp;
exports.eventType = eventType;
exports.getEndpointAnnotation = getEndpointAnnotation;
exports.getOptsAndAlertTypeAndApp = getOptsAndAlertTypeAndApp;
exports.onAlertPublished = onAlertPublished;