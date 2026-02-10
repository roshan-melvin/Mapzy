import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";

//#region src/v2/providers/testLab.ts
var testLab_exports = /* @__PURE__ */ __export({
	eventType: () => eventType,
	onTestMatrixCompleted: () => onTestMatrixCompleted
});
/** @internal */
const eventType = "google.firebase.testlab.testMatrix.v1.completed";
/**
* Event handler which triggers when a Firebase test matrix completes.
*
* @param optsOrHandler - Options or an event handler.
* @param handler - Event handler which is run every time a Firebase test matrix completes.
* @returns A Cloud Function that you can export and deploy.
* @alpha
*/
function onTestMatrixCompleted(optsOrHandler, handler) {
	if (typeof optsOrHandler === "function") {
		handler = optsOrHandler;
		optsOrHandler = {};
	}
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(optsOrHandler);
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(raw);
	};
	func.run = handler;
	const ep = {
		...initV2Endpoint(getGlobalOptions(), optsOrHandler),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		eventTrigger: {
			eventType,
			eventFilters: {},
			retry: optsOrHandler.retry ?? false
		}
	};
	func.__endpoint = ep;
	return func;
}

//#endregion
export { eventType, onTestMatrixCompleted, testLab_exports };