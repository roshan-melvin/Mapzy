import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { convertIfPresent, copyIfPresent } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";

//#region src/v2/providers/eventarc.ts
var eventarc_exports = /* @__PURE__ */ __export({ onCustomEventPublished: () => onCustomEventPublished });
function onCustomEventPublished(eventTypeOrOpts, handler) {
	let opts;
	if (typeof eventTypeOrOpts === "string") {
		opts = { eventType: eventTypeOrOpts };
	} else if (typeof eventTypeOrOpts === "object") {
		opts = eventTypeOrOpts;
	}
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(raw);
	};
	func.run = handler;
	const channel = opts.channel ?? "locations/us-central1/channels/firebase";
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
			eventType: opts.eventType,
			eventFilters: {},
			retry: opts.retry ?? false,
			channel
		}
	};
	convertIfPresent(endpoint.eventTrigger, opts, "eventFilters", "filters");
	copyIfPresent(endpoint.eventTrigger, opts, "retry");
	func.__endpoint = endpoint;
	func.__requiredAPIs = [{
		api: "eventarcpublishing.googleapis.com",
		reason: "Needed for custom event functions"
	}];
	return func;
}

//#endregion
export { eventarc_exports, onCustomEventPublished };