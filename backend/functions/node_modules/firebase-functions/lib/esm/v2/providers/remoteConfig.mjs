import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";

//#region src/v2/providers/remoteConfig.ts
var remoteConfig_exports = /* @__PURE__ */ __export({
	eventType: () => eventType,
	onConfigUpdated: () => onConfigUpdated
});
/** @internal */
const eventType = "google.firebase.remoteconfig.remoteConfig.v1.updated";
/**
* Event handler which triggers when data is updated in a Remote Config.
*
* @param optsOrHandler - Options or an event handler.
* @param handler - Event handler which is run every time a Remote Config update occurs.
* @returns A function that you can export and deploy.
*/
function onConfigUpdated(optsOrHandler, handler) {
	if (typeof optsOrHandler === "function") {
		handler = optsOrHandler;
		optsOrHandler = {};
	}
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(optsOrHandler);
	const func = wrapTraceContext(withInit((raw) => {
		return handler(raw);
	}));
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
export { eventType, onConfigUpdated, remoteConfig_exports };