import { initV2Endpoint } from "../../../runtime/manifest.mjs";
import { withInit } from "../../../common/onInit.mjs";
import { wrapTraceContext } from "../../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../../options.mjs";

//#region src/v2/providers/alerts/alerts.ts
/** @internal */
const eventType = "google.firebase.firebasealerts.alerts.v1.published";
function onAlertPublished(alertTypeOrOpts, handler) {
	const [opts, alertType, appId] = getOptsAndAlertTypeAndApp(alertTypeOrOpts);
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(convertAlertAndApp(raw));
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
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(opts);
	const endpoint = {
		...initV2Endpoint(getGlobalOptions(), opts),
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
export { convertAlertAndApp, eventType, getEndpointAnnotation, getOptsAndAlertTypeAndApp, onAlertPublished };