const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_logger_index = require('../../logger/index.js');
const require_params_types = require('../../params/types.js');
require('../../params/index.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_debug = require('../../common/debug.js');
const require_common_encoding = require('../../common/encoding.js');
const require_common_onInit = require('../../common/onInit.js');
const require_common_providers_https = require('../../common/providers/https.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');
let cors = require("cors");
cors = require_rolldown_runtime.__toESM(cors);

//#region src/v2/providers/https.ts
var https_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	HttpsError: () => require_common_providers_https.HttpsError,
	hasClaim: () => hasClaim,
	isSignedIn: () => isSignedIn,
	onCall: () => onCall,
	onCallGenkit: () => onCallGenkit,
	onRequest: () => onRequest
});
/**
* @deprecated
*
* An auth policy that requires a user to be signed in.
*/
const isSignedIn = () => (auth) => !!auth;
/**
* @deprecated
*
* An auth policy that requires a user to be both signed in and have a specific claim (optionally with a specific value)
*/
const hasClaim = (claim, value) => (auth) => {
	if (!auth) {
		return false;
	}
	if (!(claim in auth.token)) {
		return false;
	}
	return !value || auth.token[claim] === value;
};
function onRequest(optsOrHandler, handler) {
	let opts;
	if (arguments.length === 1) {
		opts = {};
		handler = optsOrHandler;
	} else {
		opts = optsOrHandler;
	}
	handler = require_common_providers_https.withErrorHandler(handler);
	if (require_common_debug.isDebugFeatureEnabled("enableCors") || "cors" in opts) {
		let origin = opts.cors instanceof require_params_types.Expression ? opts.cors.value() : opts.cors;
		if (require_common_debug.isDebugFeatureEnabled("enableCors")) {
			origin = opts.cors === false ? false : true;
		}
		if (Array.isArray(origin) && origin.length === 1) {
			origin = origin[0];
		}
		const middleware = (0, cors.default)({ origin });
		const userProvidedHandler = handler;
		handler = (req, res) => {
			return new Promise((resolve) => {
				res.on("finish", resolve);
				middleware(req, res, () => {
					resolve(userProvidedHandler(req, res));
				});
			});
		};
	}
	handler = require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler));
	Object.defineProperty(handler, "__trigger", { get: () => {
		const baseOpts$1 = require_v2_options.optionsToTriggerAnnotations(require_v2_options.getGlobalOptions());
		const specificOpts$1 = require_v2_options.optionsToTriggerAnnotations(opts);
		const trigger = {
			platform: "gcfv2",
			...baseOpts$1,
			...specificOpts$1,
			labels: {
				...baseOpts$1?.labels,
				...specificOpts$1?.labels
			},
			httpsTrigger: { allowInsecure: false }
		};
		require_common_encoding.convertIfPresent(trigger.httpsTrigger, require_v2_options.getGlobalOptions(), "invoker", "invoker", require_common_encoding.convertInvoker);
		require_common_encoding.convertIfPresent(trigger.httpsTrigger, opts, "invoker", "invoker", require_common_encoding.convertInvoker);
		return trigger;
	} });
	const globalOpts = require_v2_options.getGlobalOptions();
	const baseOpts = require_v2_options.optionsToEndpoint(globalOpts);
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	const endpoint = {
		...require_runtime_manifest.initV2Endpoint(globalOpts, opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		httpsTrigger: {}
	};
	require_common_encoding.convertIfPresent(endpoint.httpsTrigger, globalOpts, "invoker", "invoker", require_common_encoding.convertInvoker);
	require_common_encoding.convertIfPresent(endpoint.httpsTrigger, opts, "invoker", "invoker", require_common_encoding.convertInvoker);
	handler.__endpoint = endpoint;
	return handler;
}
function onCall(optsOrHandler, handler) {
	let opts;
	if (arguments.length === 1) {
		opts = {};
		handler = optsOrHandler;
	} else {
		opts = optsOrHandler;
	}
	let cors$2;
	if ("cors" in opts) {
		if (opts.cors instanceof require_params_types.Expression) {
			cors$2 = opts.cors.value();
		} else {
			cors$2 = opts.cors;
		}
	} else {
		cors$2 = true;
	}
	let origin = require_common_debug.isDebugFeatureEnabled("enableCors") ? true : cors$2;
	if (Array.isArray(origin) && origin.length === 1) {
		origin = origin[0];
	}
	const fixedLen = (req, resp) => handler(req, resp);
	let func = require_common_providers_https.onCallHandler({
		cors: {
			origin,
			methods: "POST"
		},
		enforceAppCheck: opts.enforceAppCheck ?? require_v2_options.getGlobalOptions().enforceAppCheck,
		consumeAppCheckToken: opts.consumeAppCheckToken,
		heartbeatSeconds: opts.heartbeatSeconds,
		authPolicy: opts.authPolicy
	}, fixedLen, "gcfv2");
	func = require_v2_trace.wrapTraceContext(require_common_onInit.withInit(func));
	Object.defineProperty(func, "__trigger", { get: () => {
		const baseOpts$1 = require_v2_options.optionsToTriggerAnnotations(require_v2_options.getGlobalOptions());
		const specificOpts$1 = require_v2_options.optionsToTriggerAnnotations(opts);
		return {
			platform: "gcfv2",
			...baseOpts$1,
			...specificOpts$1,
			labels: {
				...baseOpts$1?.labels,
				...specificOpts$1?.labels,
				"deployment-callable": "true"
			},
			httpsTrigger: { allowInsecure: false }
		};
	} });
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	func.__endpoint = {
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		callableTrigger: {}
	};
	func.run = require_common_onInit.withInit(handler);
	func.stream = () => {
		return {
			stream: { next() {
				return Promise.reject("Coming soon");
			} },
			output: Promise.reject("Coming soon")
		};
	};
	return func;
}
function onCallGenkit(optsOrAction, action) {
	let opts;
	if (arguments.length === 2) {
		opts = optsOrAction;
	} else {
		opts = {};
		action = optsOrAction;
	}
	if (!opts.secrets?.length) {
		require_logger_index.debug(`Genkit function for ${action.__action.name} is not bound to any secret. This may mean that you are not storing API keys as a secret or that you are not binding your secret to this function. See https://firebase.google.com/docs/functions/config-env?gen=2nd#secret_parameters for more information.`);
	}
	const cloudFunction = onCall(opts, async (req, res) => {
		const context = {};
		require_common_encoding.copyIfPresent(context, req, "auth", "app", "instanceIdToken");
		if (!req.acceptsStreaming) {
			const { result } = await action.run(req.data, { context });
			return result;
		}
		const { stream, output } = action.stream(req.data, { context });
		for await (const chunk of stream) {
			await res.sendChunk(chunk);
		}
		return output;
	});
	cloudFunction.__endpoint.callableTrigger.genkitAction = action.__action.name;
	return cloudFunction;
}

//#endregion
exports.HttpsError = require_common_providers_https.HttpsError;
exports.hasClaim = hasClaim;
Object.defineProperty(exports, 'https_exports', {
  enumerable: true,
  get: function () {
    return https_exports;
  }
});
exports.isSignedIn = isSignedIn;
exports.onCall = onCall;
exports.onCallGenkit = onCallGenkit;
exports.onRequest = onRequest;