const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_onInit = require('../../common/onInit.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');

//#region src/v2/providers/remoteConfig.ts
var remoteConfig_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
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
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(optsOrHandler);
	const func = require_v2_trace.wrapTraceContext(require_common_onInit.withInit((raw) => {
		return handler(raw);
	}));
	func.run = handler;
	const ep = {
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), optsOrHandler),
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
exports.eventType = eventType;
exports.onConfigUpdated = onConfigUpdated;
Object.defineProperty(exports, 'remoteConfig_exports', {
  enumerable: true,
  get: function () {
    return remoteConfig_exports;
  }
});