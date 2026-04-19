import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { debug } from "../../logger/index.mjs";
import { Expression } from "../../params/types.mjs";
import "../../params/index.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { isDebugFeatureEnabled } from "../../common/debug.mjs";
import { convertIfPresent, convertInvoker, copyIfPresent } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { HttpsError, onCallHandler, withErrorHandler } from "../../common/providers/https.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint, optionsToTriggerAnnotations } from "../options.mjs";
import cors from "cors";

//#region src/v2/providers/https.ts
var https_exports = /* @__PURE__ */ __export({
	HttpsError: () => HttpsError,
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
	handler = withErrorHandler(handler);
	if (isDebugFeatureEnabled("enableCors") || "cors" in opts) {
		let origin = opts.cors instanceof Expression ? opts.cors.value() : opts.cors;
		if (isDebugFeatureEnabled("enableCors")) {
			origin = opts.cors === false ? false : true;
		}
		if (Array.isArray(origin) && origin.length === 1) {
			origin = origin[0];
		}
		const middleware = cors({ origin });
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
	handler = wrapTraceContext(withInit(handler));
	Object.defineProperty(handler, "__trigger", { get: () => {
		const baseOpts$1 = optionsToTriggerAnnotations(getGlobalOptions());
		const specificOpts$1 = optionsToTriggerAnnotations(opts);
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
		convertIfPresent(trigger.httpsTrigger, getGlobalOptions(), "invoker", "invoker", convertInvoker);
		convertIfPresent(trigger.httpsTrigger, opts, "invoker", "invoker", convertInvoker);
		return trigger;
	} });
	const globalOpts = getGlobalOptions();
	const baseOpts = optionsToEndpoint(globalOpts);
	const specificOpts = optionsToEndpoint(opts);
	const endpoint = {
		...initV2Endpoint(globalOpts, opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		httpsTrigger: {}
	};
	convertIfPresent(endpoint.httpsTrigger, globalOpts, "invoker", "invoker", convertInvoker);
	convertIfPresent(endpoint.httpsTrigger, opts, "invoker", "invoker", convertInvoker);
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
	let cors$1;
	if ("cors" in opts) {
		if (opts.cors instanceof Expression) {
			cors$1 = opts.cors.value();
		} else {
			cors$1 = opts.cors;
		}
	} else {
		cors$1 = true;
	}
	let origin = isDebugFeatureEnabled("enableCors") ? true : cors$1;
	if (Array.isArray(origin) && origin.length === 1) {
		origin = origin[0];
	}
	const fixedLen = (req, resp) => handler(req, resp);
	let func = onCallHandler({
		cors: {
			origin,
			methods: "POST"
		},
		enforceAppCheck: opts.enforceAppCheck ?? getGlobalOptions().enforceAppCheck,
		consumeAppCheckToken: opts.consumeAppCheckToken,
		heartbeatSeconds: opts.heartbeatSeconds,
		authPolicy: opts.authPolicy
	}, fixedLen, "gcfv2");
	func = wrapTraceContext(withInit(func));
	Object.defineProperty(func, "__trigger", { get: () => {
		const baseOpts$1 = optionsToTriggerAnnotations(getGlobalOptions());
		const specificOpts$1 = optionsToTriggerAnnotations(opts);
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
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(opts);
	func.__endpoint = {
		...initV2Endpoint(getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		callableTrigger: {}
	};
	func.run = withInit(handler);
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
		debug(`Genkit function for ${action.__action.name} is not bound to any secret. This may mean that you are not storing API keys as a secret or that you are not binding your secret to this function. See https://firebase.google.com/docs/functions/config-env?gen=2nd#secret_parameters for more information.`);
	}
	const cloudFunction = onCall(opts, async (req, res) => {
		const context = {};
		copyIfPresent(context, req, "auth", "app", "instanceIdToken");
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
export { HttpsError, hasClaim, https_exports, isSignedIn, onCall, onCallGenkit, onRequest };