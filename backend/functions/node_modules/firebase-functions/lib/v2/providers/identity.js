const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_onInit = require('../../common/onInit.js');
const require_common_providers_https = require('../../common/providers/https.js');
const require_common_providers_identity = require('../../common/providers/identity.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');

//#region src/v2/providers/identity.ts
var identity_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	HttpsError: () => require_common_providers_https.HttpsError,
	beforeEmailSent: () => beforeEmailSent,
	beforeOperation: () => beforeOperation,
	beforeSmsSent: () => beforeSmsSent,
	beforeUserCreated: () => beforeUserCreated,
	beforeUserSignedIn: () => beforeUserSignedIn,
	getOpts: () => getOpts
});
/**
* Handles an event that is triggered before a user is created.
* @param optsOrHandler - Either an object containing function options, or an event handler (run before user creation).
* @param handler? - If defined, an event handler which is run every time before a user is created.
*/
function beforeUserCreated(optsOrHandler, handler) {
	return beforeOperation("beforeCreate", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before a user is signed in.
* @param optsOrHandler - Either an object containing function options, or an event handler (run before user signin).
* @param handler - Event handler which is run every time before a user is signed in.
*/
function beforeUserSignedIn(optsOrHandler, handler) {
	return beforeOperation("beforeSignIn", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before an email is sent to a user.
* @param optsOrHandler- Either an object containing function options, or an event handler that is run before an email is sent to a user.
* @param handler - Event handler that is run before an email is sent to a user.
*/
function beforeEmailSent(optsOrHandler, handler) {
	return beforeOperation("beforeSendEmail", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before an SMS is sent to a user.
* @param optsOrHandler - Either an object containing function options, or an event handler that is run before an SMS is sent to a user.
* @param handler - Event handler that is run before an SMS is sent to a user.
*/
function beforeSmsSent(optsOrHandler, handler) {
	return beforeOperation("beforeSendSms", optsOrHandler, handler);
}
/** @hidden */
function beforeOperation(eventType, optsOrHandler, handler) {
	if (!handler || typeof optsOrHandler === "function") {
		handler = optsOrHandler;
		optsOrHandler = {};
	}
	const { opts,...blockingOptions } = getOpts(optsOrHandler);
	const annotatedHandler = Object.assign(handler, { platform: "gcfv2" });
	const func = require_v2_trace.wrapTraceContext(require_common_onInit.withInit(require_common_providers_identity.wrapHandler(eventType, annotatedHandler)));
	const legacyEventType = `providers/cloud.auth/eventTypes/user.${eventType}`;
	/** Endpoint */
	const baseOptsEndpoint = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOptsEndpoint = require_v2_options.optionsToEndpoint(opts);
	func.__endpoint = {
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOptsEndpoint,
		...specificOptsEndpoint,
		labels: {
			...baseOptsEndpoint?.labels,
			...specificOptsEndpoint?.labels
		},
		blockingTrigger: {
			eventType: legacyEventType,
			options: { ...(eventType === "beforeCreate" || eventType === "beforeSignIn") && blockingOptions }
		}
	};
	func.__requiredAPIs = [{
		api: "identitytoolkit.googleapis.com",
		reason: "Needed for auth blocking functions"
	}];
	func.run = handler;
	return func;
}
/** @hidden */
function getOpts(blockingOptions) {
	const accessToken = blockingOptions.accessToken || false;
	const idToken = blockingOptions.idToken || false;
	const refreshToken = blockingOptions.refreshToken || false;
	const opts = { ...blockingOptions };
	delete opts.accessToken;
	delete opts.idToken;
	delete opts.refreshToken;
	return {
		opts,
		accessToken,
		idToken,
		refreshToken
	};
}

//#endregion
exports.HttpsError = require_common_providers_https.HttpsError;
exports.beforeEmailSent = beforeEmailSent;
exports.beforeOperation = beforeOperation;
exports.beforeSmsSent = beforeSmsSent;
exports.beforeUserCreated = beforeUserCreated;
exports.beforeUserSignedIn = beforeUserSignedIn;
exports.getOpts = getOpts;
Object.defineProperty(exports, 'identity_exports', {
  enumerable: true,
  get: function () {
    return identity_exports;
  }
});