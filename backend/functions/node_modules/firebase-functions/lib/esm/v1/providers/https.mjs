import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV1Endpoint } from "../../runtime/manifest.mjs";
import { convertIfPresent, convertInvoker } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { optionsToEndpoint, optionsToTrigger } from "../cloud-functions.mjs";
import { HttpsError, onCallHandler, withErrorHandler } from "../../common/providers/https.mjs";
import { wrapTraceContext } from "../../v2/trace.mjs";

//#region src/v1/providers/https.ts
var https_exports = /* @__PURE__ */ __export({
	HttpsError: () => HttpsError,
	_onCallWithOptions: () => _onCallWithOptions,
	_onRequestWithOptions: () => _onRequestWithOptions,
	onCall: () => onCall,
	onRequest: () => onRequest
});
/**
* Handle HTTP requests.
* @param handler A function that takes a request and response object,
* same signature as an Express app.
*/
function onRequest(handler) {
	return _onRequestWithOptions(handler, {});
}
/**
* Declares a callable method for clients to call using a Firebase SDK.
* @param handler A method that takes a data and context and returns a value.
*/
function onCall(handler) {
	return _onCallWithOptions(handler, {});
}
/** @internal */
function _onRequestWithOptions(handler, options) {
	const cloudFunction = (req, res) => {
		return wrapTraceContext(withInit(withErrorHandler(handler)))(req, res);
	};
	cloudFunction.__trigger = {
		...optionsToTrigger(options),
		httpsTrigger: {}
	};
	convertIfPresent(cloudFunction.__trigger.httpsTrigger, options, "invoker", "invoker", convertInvoker);
	cloudFunction.__endpoint = {
		platform: "gcfv1",
		...initV1Endpoint(options),
		...optionsToEndpoint(options),
		httpsTrigger: {}
	};
	convertIfPresent(cloudFunction.__endpoint.httpsTrigger, options, "invoker", "invoker", convertInvoker);
	return cloudFunction;
}
/** @internal */
function _onCallWithOptions(handler, options) {
	const fixedLen = (data, context) => {
		return withInit(handler)(data, context);
	};
	const func = wrapTraceContext(onCallHandler({
		enforceAppCheck: options.enforceAppCheck,
		consumeAppCheckToken: options.consumeAppCheckToken,
		cors: {
			origin: true,
			methods: "POST"
		}
	}, fixedLen, "gcfv1"));
	func.__trigger = {
		labels: {},
		...optionsToTrigger(options),
		httpsTrigger: {}
	};
	func.__trigger.labels["deployment-callable"] = "true";
	func.__endpoint = {
		platform: "gcfv1",
		labels: {},
		...initV1Endpoint(options),
		...optionsToEndpoint(options),
		callableTrigger: {}
	};
	func.run = fixedLen;
	return func;
}

//#endregion
export { HttpsError, _onCallWithOptions, _onRequestWithOptions, https_exports, onCall, onRequest };