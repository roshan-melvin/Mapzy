const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_encoding = require('../../common/encoding.js');
const require_common_onInit = require('../../common/onInit.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');

//#region src/v2/providers/eventarc.ts
var eventarc_exports = /* @__PURE__ */ require_rolldown_runtime.__export({ onCustomEventPublished: () => onCustomEventPublished });
function onCustomEventPublished(eventTypeOrOpts, handler) {
	let opts;
	if (typeof eventTypeOrOpts === "string") {
		opts = { eventType: eventTypeOrOpts };
	} else if (typeof eventTypeOrOpts === "object") {
		opts = eventTypeOrOpts;
	}
	const func = (raw) => {
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(raw);
	};
	func.run = handler;
	const channel = opts.channel ?? "locations/us-central1/channels/firebase";
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
			eventType: opts.eventType,
			eventFilters: {},
			retry: opts.retry ?? false,
			channel
		}
	};
	require_common_encoding.convertIfPresent(endpoint.eventTrigger, opts, "eventFilters", "filters");
	require_common_encoding.copyIfPresent(endpoint.eventTrigger, opts, "retry");
	func.__endpoint = endpoint;
	func.__requiredAPIs = [{
		api: "eventarcpublishing.googleapis.com",
		reason: "Needed for custom event functions"
	}];
	return func;
}

//#endregion
Object.defineProperty(exports, 'eventarc_exports', {
  enumerable: true,
  get: function () {
    return eventarc_exports;
  }
});
exports.onCustomEventPublished = onCustomEventPublished;