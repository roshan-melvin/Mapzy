const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_encoding = require('../../common/encoding.js');
const require_common_onInit = require('../../common/onInit.js');
const require_v1_cloud_functions = require('../cloud-functions.js');
const require_common_providers_https = require('../../common/providers/https.js');
const require_v2_trace = require('../../v2/trace.js');

//#region src/v1/providers/https.ts
var https_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	HttpsError: () => require_common_providers_https.HttpsError,
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
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(require_common_providers_https.withErrorHandler(handler)))(req, res);
	};
	cloudFunction.__trigger = {
		...require_v1_cloud_functions.optionsToTrigger(options),
		httpsTrigger: {}
	};
	require_common_encoding.convertIfPresent(cloudFunction.__trigger.httpsTrigger, options, "invoker", "invoker", require_common_encoding.convertInvoker);
	cloudFunction.__endpoint = {
		platform: "gcfv1",
		...require_runtime_manifest.initV1Endpoint(options),
		...require_v1_cloud_functions.optionsToEndpoint(options),
		httpsTrigger: {}
	};
	require_common_encoding.convertIfPresent(cloudFunction.__endpoint.httpsTrigger, options, "invoker", "invoker", require_common_encoding.convertInvoker);
	return cloudFunction;
}
/** @internal */
function _onCallWithOptions(handler, options) {
	const fixedLen = (data, context) => {
		return require_common_onInit.withInit(handler)(data, context);
	};
	const func = require_v2_trace.wrapTraceContext(require_common_providers_https.onCallHandler({
		enforceAppCheck: options.enforceAppCheck,
		consumeAppCheckToken: options.consumeAppCheckToken,
		cors: {
			origin: true,
			methods: "POST"
		}
	}, fixedLen, "gcfv1"));
	func.__trigger = {
		labels: {},
		...require_v1_cloud_functions.optionsToTrigger(options),
		httpsTrigger: {}
	};
	func.__trigger.labels["deployment-callable"] = "true";
	func.__endpoint = {
		platform: "gcfv1",
		labels: {},
		...require_runtime_manifest.initV1Endpoint(options),
		...require_v1_cloud_functions.optionsToEndpoint(options),
		callableTrigger: {}
	};
	func.run = fixedLen;
	return func;
}

//#endregion
exports.HttpsError = require_common_providers_https.HttpsError;
exports._onCallWithOptions = _onCallWithOptions;
exports._onRequestWithOptions = _onRequestWithOptions;
Object.defineProperty(exports, 'https_exports', {
  enumerable: true,
  get: function () {
    return https_exports;
  }
});
exports.onCall = onCall;
exports.onRequest = onRequest;