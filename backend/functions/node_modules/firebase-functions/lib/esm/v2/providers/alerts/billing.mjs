import { __export } from "../../../_virtual/rolldown_runtime.mjs";
import { withInit } from "../../../common/onInit.mjs";
import { wrapTraceContext } from "../../trace.mjs";
import { convertAlertAndApp, getEndpointAnnotation } from "./alerts.mjs";

//#region src/v2/providers/alerts/billing.ts
var billing_exports = /* @__PURE__ */ __export({
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
		return wrapTraceContext(withInit(handler))(convertAlertAndApp(raw));
	};
	func.run = handler;
	func.__endpoint = getEndpointAnnotation(optsOrHandler, alertType);
	return func;
}

//#endregion
export { billing_exports, onOperation, onPlanAutomatedUpdatePublished, onPlanUpdatePublished, planAutomatedUpdateAlert, planUpdateAlert };