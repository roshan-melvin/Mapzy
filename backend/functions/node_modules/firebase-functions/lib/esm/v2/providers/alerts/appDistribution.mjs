import { __export } from "../../../_virtual/rolldown_runtime.mjs";
import { withInit } from "../../../common/onInit.mjs";
import { wrapTraceContext } from "../../trace.mjs";
import { convertAlertAndApp, getEndpointAnnotation } from "./alerts.mjs";

//#region src/v2/providers/alerts/appDistribution.ts
var appDistribution_exports = /* @__PURE__ */ __export({
	getOptsAndApp: () => getOptsAndApp,
	inAppFeedbackAlert: () => inAppFeedbackAlert,
	newTesterIosDeviceAlert: () => newTesterIosDeviceAlert,
	onInAppFeedbackPublished: () => onInAppFeedbackPublished,
	onNewTesterIosDevicePublished: () => onNewTesterIosDevicePublished
});
/** @internal */
const newTesterIosDeviceAlert = "appDistribution.newTesterIosDevice";
/** @internal */
const inAppFeedbackAlert = "appDistribution.inAppFeedback";
/**
* Declares a function that can handle adding a new tester iOS device.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler which is run every time a new tester iOS device is added.
* @returns A function that you can export and deploy.
*/
function onNewTesterIosDevicePublished(appIdOrOptsOrHandler, handler) {
	if (typeof appIdOrOptsOrHandler === "function") {
		handler = appIdOrOptsOrHandler;
		appIdOrOptsOrHandler = {};
	}
	const [opts, appId] = getOptsAndApp(appIdOrOptsOrHandler);
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = getEndpointAnnotation(opts, newTesterIosDeviceAlert, appId);
	return func;
}
/**
* Declares a function that can handle receiving new in-app feedback from a tester.
* @param appIdOrOptsOrHandler - A specific application, options, or an event-handling function.
* @param handler - Event handler which is run every time new feedback is received.
* @returns A function that you can export and deploy.
*/
function onInAppFeedbackPublished(appIdOrOptsOrHandler, handler) {
	if (typeof appIdOrOptsOrHandler === "function") {
		handler = appIdOrOptsOrHandler;
		appIdOrOptsOrHandler = {};
	}
	const [opts, appId] = getOptsAndApp(appIdOrOptsOrHandler);
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = getEndpointAnnotation(opts, inAppFeedbackAlert, appId);
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
export { appDistribution_exports, getOptsAndApp, inAppFeedbackAlert, newTesterIosDeviceAlert, onInAppFeedbackPublished, onNewTesterIosDevicePublished };